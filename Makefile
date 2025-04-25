# java compiler
JAVAC = javac
JAVA = java

# source files
SERVER_SRC = TCPServer.java
CLIENT_SRC = TCPClient.java

SERVER_CLASS = TCPServer.class
CLIENT_CLASS = TCPClient.class

# compiles both
all: server client

# compile the server
server: $(SERVER_SRC)
	$(JAVAC) $(SERVER_SRC)

# compile the client
client: $(CLIENT_SRC)
	$(JAVAC) $(CLIENT_SRC)

# run the server
run-server: server
	$(JAVA) TCPServer

# run the client
run-client: client
	$(JAVA) TCPClient

# clean up .class files
clean:
	rm -f *.class