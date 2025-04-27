import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

class Client {
    private String address;
    private LocalDateTime connectionTime;
    private String username;

    public Client(String address, String username) {
        this.address = address;
        this.username = username;
        this.connectionTime = LocalDateTime.now();
    }
    public String getAddress() {
        return address;
    }
    public LocalDateTime getConnectionTime() {
        return connectionTime;
    }
    public String getUsername() {
        return username;
    }
    public String getDuration() {
        Duration duration = Duration.between(connectionTime, LocalDateTime.now());
        return formatDuration(duration);
    }
    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String connTime = connectionTime.format(formatter);

        return String.format("User: %s, IP: %s, Connected: %s, Duration: %s",
                username, address, connTime, getDuration());
    }
}

class ClientLogger {
    private List<Client> clients = new ArrayList<>();
    private List<String> activityLog = new ArrayList<>();
    private final Object lock = new Object();

    public void logConnection(Client client) {
        synchronized (lock) {
            clients.add(client);
            String logEntry = String.format("[%s] Connection established: %s (%s)",
                    getCurrentTime(), client.getUsername(), client.getAddress());
            activityLog.add(logEntry);
            System.out.println(logEntry);
        }
    }

    public void logActivity(Client client, String activity) {
        synchronized (lock) {
            String logEntry = String.format("[%s] User %s (%s): %s",
                    getCurrentTime(), client.getUsername(), client.getAddress(), activity);
            activityLog.add(logEntry);
            System.out.println(logEntry);
        }
    }

    public void removeClient(Client client) {
        synchronized (lock) {
            clients.remove(client);
            String logEntry = String.format("[%s] Disconnection: %s (%s)",
                    getCurrentTime(), client.getUsername(), client.getAddress());
            activityLog.add(logEntry);
            System.out.println(logEntry);
        }
    }

    private String getCurrentTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }

    public void printClients() {
        synchronized (lock) {
            System.out.println("\nConnected Clients:");
            if (clients.isEmpty()) {
                System.out.println("No clients\n");
            } else {
                for (Client client : clients) {
                    System.out.println(client);
                }
            }
        }
    }
}

class RequestPriority {
    private List<Runnable> queue = new ArrayList<>();
    private final Object lock = new Object();

    public void addRequst(Runnable request) {
        synchronized (lock) {
            queue.add(request);
        }
    }

    public void processNext() {
        synchronized (lock) {
            if (!queue.isEmpty()) {
                Runnable next = queue.remove(0);
                next.run();
            }
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader inFromClient;
    private DataOutputStream outToClient;
    private Client client;
    private ClientLogger logger;
    private RequestPriority requestOrderQueue;

    public ClientHandler(Socket socket, ClientLogger logger, RequestPriority queue) {
        try {
            this.clientSocket = socket;
            this.inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.outToClient = new DataOutputStream(clientSocket.getOutputStream());
            this.logger = logger;
            this.requestOrderQueue = queue;
        } catch (IOException e) {
            System.out.println("Error initializing client handler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            //Handle connection handshake
            String joinMessage = inFromClient.readLine();
            if (joinMessage.startsWith("JOIN:")) {
                String username = joinMessage.substring(5);
                String clientAddress = clientSocket.getInetAddress().getHostAddress();
                client = new Client(clientAddress, username);
                logger.logConnection(client);
                outToClient.writeBytes("Hello " + username + "\n");

                //Procss client requests
                while (true) {
                    String clientMessage = inFromClient.readLine();
                    if (clientMessage == null) {
                        logger.removeClient(client);
                        break; //Client disconnected
                    }

                    if (clientMessage.startsWith("CLOSE")) {
                        logger.logActivity(client, "Requested disconnection");
                        logger.removeClient(client);  // remove client on request 
                        outToClient.writeBytes("Connection closed\n"); // send final confirmation before closing
                        break; // close client connection cleanly
                    }

                    //Handle math calculation
                    if (clientMessage.startsWith("CALC:")) {
                        this.requestOrderQueue.addRequst(() -> handleCalculation(clientMessage.substring(5)));
                    } else {
                        outToClient.writeBytes("Error: Invalid command\n");
                    }

                }
            } else {
                outToClient.writeBytes("Error: Invalid join format. Use JOIN:username\n");
            }
        } catch (IOException e) {
            System.out.println("Client handler error: " + e.getMessage());
        } finally {
            //close connections
            try{
            //close server side too
                if(inFromClient!=null){
                    inFromClient.close();
                }
                if(outToClient!=null){
                    outToClient.close();
                }
                if(clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                if(client != null) {
                    logger.logActivity(client,"Disconnected.");
                }
                
            } catch (IOException e) {
                System.out.println("Error closing connection: " + e.getMessage());
            }
            
        }
    }

    private void handleCalculation(String calculation) {
        try {
            // Log the calculation request
            logger.logActivity(client, "Calculation: " + calculation);
            // Parse and calculate
            String result = evaluateMathExpression(calculation);
            // Send result back to client
            outToClient.writeBytes("Result: " + result + "\n");
            logger.logActivity(client, "Answer from Server: " + result);
        } catch (Exception e) {
            try {
                outToClient.writeBytes("Error: " + e.getMessage() + " or contains negative value\n");
            } catch (IOException ioe) {
                System.out.println("Error sending calculation error to client: " + ioe.getMessage());
            }
        }
    }

    private String evaluateMathExpression(String expression) {
        
        expression = expression.replaceAll("\\s+", "");
        List<Double> nums = new ArrayList<>();
        List<Character> ops = new ArrayList<>();


        // Initial parsing of the first number
        StringBuilder numBuilder = new StringBuilder();

    
        for(int i =0; i<expression.length();i++){
            char ch = expression.charAt(i);
            if(Character.isDigit(ch)|| ch=='.'){
                numBuilder.append(ch);
            }else{
                nums.add(Double.parseDouble(numBuilder.toString()));
                numBuilder.setLength(0);
                ops.add(ch);
            }
        }
        nums.add(Double.parseDouble(numBuilder.toString()));

        //handling *,/, and %
        for(int i =0; i < ops.size();){
            char op = ops.get(i);
            if(op == '*'||op=='/'||op =='%'){
                double x =nums.get(i);
                double y =nums.get(i+1);
                double result = 0;

                if(op == '*'){
                    result = x*y;
                } else if (op == '/'){
                    if(y==0){
                        return "Error: Dividing by 0";
                    }

                    result = x/y;
                }else if(op == '%'){
                    if(y==0){
                        return "Error: Modulo by 0";
                    }

                    result = x % y;
                }

                nums.set(i,result);
                nums.remove(i+1);
                ops.remove(i);
            } else{
                i++;
            }
        }

        //handling +, -
        double result = nums.get(0);
        for(int i =0; i < ops.size(); i++){
            char op =ops.get(i);
            double y = nums.get(i+1);
            if(op=='+'){
                result+=y;
            }else{
                result -=y;    
            }
        }

        if(result == (int)result){
            return String.valueOf((int) result);
        } else{
            return String.valueOf(result);
        }
    }
}
public class TCPServer {
    private static final int PORT = 6789;
    private static ClientLogger logger = new ClientLogger();
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    private static RequestPriority queue = new RequestPriority();

    public static void main(String[] argv) throws Exception {
        ServerSocket welcomeSocket = new ServerSocket(PORT);
        System.out.println("Math Server started on port " + PORT);
        System.out.println("Waiting for clients to connect...");

        //Start server monitor thread
        startServerMonitor();

        // Start request calculation monitoring
        startCalculationMonitoring(queue);

        //Accept client connections
        while (true) {
            Socket connectionSocket = welcomeSocket.accept();
            System.out.println("New client connected: " + connectionSocket.getInetAddress().getHostAddress());

            //Create a new thread for each client
            ClientHandler clientHandler = new ClientHandler(connectionSocket, logger, queue);
            threadPool.execute(clientHandler);
        }
    }

    private static void startCalculationMonitoring(RequestPriority requestPriority) {
        Thread monitor = new Thread(() -> {
            while (true) {
                requestPriority.processNext();
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }

    private static void startServerMonitor() {
        Thread monitor = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.println("\nServer Commands:");
                System.out.println("1. Show connected clients");
                System.out.println("2. Exit server");
                System.out.print("Enter command (1-2): ");

                try {
                    int command = scanner.nextInt();
                    switch (command) {
                        case 1:
                            logger.printClients();
                            break;
                        case 2:
                            System.out.println("Shutting down server...");
                            threadPool.shutdown();
                            scanner.close();
                            System.exit(0);
                            break;
                        default:
                            System.out.println("Invalid command");
                    }
                } catch (Exception e) {
                    System.out.println("Invalid input: " + e.getMessage());
                    scanner.nextLine(); //Clear the scanner buffer
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        monitor.setDaemon(true);
        monitor.start();
    }
}

