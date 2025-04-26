import java.io.*;
import java.net.*;
import java.util.*;

public class TCPClient {

    public static void main(String[] args) {
        final String SERVER_ADDRESS = "127.0.0.1"; // localhost
        final int SERVER_PORT = 6789; // port server
        Scanner scanner = new Scanner(System.in);

        try (Socket clientSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream())) {

            // send JOIN request with user-provided name
            System.out.print("Please type your name to connect: ");
            String username = scanner.nextLine().trim();
            outToServer.writeBytes("JOIN:" + username + "\n");

            // wait for server acknowledgment
            String serverResponse = inFromServer.readLine();
            System.out.println("Server: " + serverResponse);

            // exit if connection fails
            if (!serverResponse.startsWith("Hello")) {
                System.out.println("Connection failed. Exiting.");
                return;
            }

            // send 3 expressions with at least 2 operators
            for (int i = 0; i < 3; i++) {
                String expression = getValidExpression(scanner);
                outToServer.writeBytes("CALC:" + expression + "\n");

                String response = inFromServer.readLine();
                System.out.println("Server Response: " + response);

                // random delay
                Thread.sleep(1000 + new Random().nextInt(2000)); 
            }

            // allow continued interaction (either disconnet or send more requests)
            while (true) {
                System.out.print("Type 'CLOSE' to disconnect or enter another expression: ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("CLOSE")) {
                    // send close command
                    outToServer.writeBytes("CLOSE\n"); 
                    // close socket 
                    clientSocket.close();              
                    System.out.println("Connection closed.");
                    break;
                }

                if (!isValidExpression(input)) {
                    System.out.println("Expression must contain at least two arithmetic operators (+ - * / %). Try again.");
                    continue;
                }

                outToServer.writeBytes("CALC:" + input + "\n");
                String response = inFromServer.readLine();
                System.out.println("Server Response: " + response);
            }

        } catch (IOException e) {
            System.err.println("Client Error: " + e.getMessage());

        } catch (InterruptedException e) {
            System.err.println("Thread interrupted.");
        }
    }

    // repeatedly prompt until a valid expression is entered
    private static String getValidExpression(Scanner scanner) {
        while (true) {
            System.out.print("Enter math expression (must include at least 2 of + - * / %): ");
            String expression = scanner.nextLine().trim();
            if (isValidExpression(expression)) return expression;
            System.out.println("Invalid. Expression must contain at least two operators. Try again.");
        }
    }

    // validate equation format is in infix notation 
    private static boolean isValidExpression(String expr) {
        expr = expr.replaceAll("\\s+", ""); 

        if (expr.isEmpty()) {
            return false;
        }

        int operatorCount = 0;
        boolean expectNumber = true; 

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);

            if (Character.isDigit(c) || c == '.') {
                expectNumber = false; 
            } else if ("+-*/%".indexOf(c) >= 0) {
                if (expectNumber && !(c == '-' && i == 0)) {
                    return false;
                }
                operatorCount++;
                expectNumber = true; 
            } else {
                return false; 
            }
        }

        if (expectNumber) {
            return false;
        }

        return operatorCount >= 2;
    }
}