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

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader inFromClient;
    private DataOutputStream outToClient;
    private Client client;
    private ClientLogger logger;

    public ClientHandler(Socket socket, ClientLogger logger) {
        try {
            this.clientSocket = socket;
            this.inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.outToClient = new DataOutputStream(clientSocket.getOutputStream());
            this.logger = logger;
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
                        break; //Client disconnected
                    }

                    //Handle math calculation
                    if (clientMessage.startsWith("CALC:")) {
                        handleCalculation(clientMessage.substring(5));
                    } else {
                        outToClient.writeBytes("Error: Invalid command\n");
                    }
                }
            } else {
                outToClient.writeBytes("Error: Invalid join format. Use JOIN:username\n");
            }
        } catch (IOException e) {
            System.out.println("Client handler error: " + e.getMessage());
        }
    }

    private void handleCalculation(String calculation) {
        try {
            //Log the calculation request
            logger.logActivity(client, "Calculation: " + calculation);

            //Pasre and calculate
            String result = evaluateMathExpression(calculation);

            //Send result bcak to client
            outToClient.writeBytes("Result: " + result + "\n");
        } catch (Exception e) {
            try {
                outToClient.writeBytes("ERROR:" + e.getMessage() + "\n");
            } catch (IOException ioe) {
                System.out.println("Error sending calculation error to client: " + ioe.getMessage());
            }
        }
    }

    private String evaluateMathExpression(String expression) {
        try {
            //Basic expression parser for arithmetic
            expression = expression.replaceAll("\\s+", "");

            //Check for basic operations
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                return String.valueOf(Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]));
            } else if (expression.contains("-")) {
                String[] parts = expression.split("-");
                return String.valueOf(Double.parseDouble(parts[0]) - Double.parseDouble(parts[1]));
            } else if (expression.contains("*")) {
                String[] parts = expression.split("\\*");
                return String.valueOf(Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]));
            } else if (expression.contains("/")) {
                String[] parts = expression.split("/");
                if (Double.parseDouble(parts[1]) == 0) {
                    return "Error: Division by zero";
                }
                return String.valueOf(Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]));
            } else {
                return "Error: Unsupported operation";
            }
        } catch (Exception e) {
            return "Error: Invalid expression";
        }
    }
}

public class TCPServer {
    private static final int PORT = 6789;
    private static ClientLogger logger = new ClientLogger();
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] argv) throws Exception {
        ServerSocket welcomeSocket = new ServerSocket(PORT);
        System.out.println("Math Server started on port " + PORT);
        System.out.println("Waiting for clients to connect...");

        //Start server monitor thread
        startServerMonitor();

        //Accept client connections
        while (true) {
            Socket connectionSocket = welcomeSocket.accept();
            System.out.println("New client connected: " + connectionSocket.getInetAddress().getHostAddress());

            //Create a new thread for each client
            ClientHandler clientHandler = new ClientHandler(connectionSocket, logger);
            threadPool.execute(clientHandler);
        }
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