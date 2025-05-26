package com.mycompany.sockets;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;        // O socket da ligação com este cliente específico.
    private final BingoServer bingoServer;    // Uma referência ao objeto principal do servidor, para interagir com ele.
    private PrintWriter out;
    private BufferedReader in;

    private String playerName;
    private String playerCardId;
    private boolean isReady = false;

    public ClientHandler(Socket socket, BingoServer server) {
        this.clientSocket = socket;
        this.bingoServer = server;

        try {
            // Inicializa os streams de entrada e saída para comunicar com o cliente.            
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            System.out.println("ClientHandler: Streams criados para " + clientSocket.getRemoteSocketAddress());
        } catch (IOException e) {
            System.err.println("ClientHandler (" + (clientSocket != null ? clientSocket.getRemoteSocketAddress() : "socket nulo") + "): Erro ao configurar streams: " + e.getMessage());
            // Se os streams não puderem ser criados, este handler não pode funcionar.
            closeResources(); // Tenta fechar o que foi aberto.
        }
    }

    //Ouve continuamente por mensagens do cliente e processa-as.
    @Override
    public void run() {
        String clientIdentifierForLogs = clientSocket.getRemoteSocketAddress().toString(); // Para logs antes do nome ser conhecido

        try {
            // 1. Processar o registo do jogador
            String registrationMessage = in.readLine(); // Lê a primeira linha enviada pelo cliente

            if (registrationMessage != null && registrationMessage.startsWith("REGISTER:")) {
                // Extrai o nome do jogador da mensagem "REGISTER:<nome>"
                this.playerName = registrationMessage.substring("REGISTER:".length()).trim();
                clientIdentifierForLogs = this.playerName; // Atualiza o identificador para logs futuros

                if (this.playerName.isEmpty()) {
                    sendMessage("ERROR:O nome não pode estar vazio.");
                    System.err.println("ClientHandler: Cliente " + clientSocket.getRemoteSocketAddress() + " enviou nome vazio.");
                    return;
                }

                // Informa o BingoServer para registar este jogador, gerar o cartão, o ID do cartão, e enviar essas informações
                // de volta ao cliente através deste handler.
                bingoServer.registerPlayer(this); // Passa a referência deste próprio handler

            } else {
                // Se a primeira mensagem não for de registo, é um erro de protocolo.
                sendMessage("ERROR:Registo inválido. Esperado: REGISTER:<nome>");
                System.err.println("ClientHandler: Cliente " + clientSocket.getRemoteSocketAddress() + " enviou mensagem de registo inválida: " + registrationMessage);
                return;
            }

            // 2. Loop principal para receber e processar outras mensagens do cliente
            // Este loop continua enquanto a ligação estiver ativa e o cliente enviar mensagens.
            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                System.out.println("Recebido de " + playerName + " (Cartão " + playerCardId + "): " + clientMessage);

                // Processa a mensagem com base no seu conteúdo
                if (clientMessage.equalsIgnoreCase("READY")) {
                    if (!this.isReady) { // Processa apenas se não estiver já "Pronto"
                        this.isReady = true;
                        bingoServer.playerIsReady(this);
                    }
                } else if (clientMessage.startsWith("CLAIM_LINE:")) {
                    bingoServer.handleClaimLine(this);
                } else if (clientMessage.startsWith("CLAIM_BINGO:")) {
                    bingoServer.handleClaimBingo(this);
                } else {
                    sendMessage("ERROR:Comando desconhecido: " + clientMessage);
                }
            }
        } catch (SocketException se) {
            // Esta exceção ocorre frequentemente quando o cliente se desconecta abruptamente (ex: fecha a janela).
            System.out.println("ClientHandler: Cliente " + clientIdentifierForLogs + " desconectou-se (SocketException: " + se.getMessage() + ").");
        } catch (IOException e) {

            if (!clientSocket.isClosed()) { // Só mostra o erro se o socket não foi intencionalmente fechado.
                System.err.println("ClientHandler para " + clientIdentifierForLogs + ": IOException: " + e.getMessage());

            }
        } catch (Exception e) {

            System.err.println("ClientHandler para " + clientIdentifierForLogs + ": Erro inesperado: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Bloco finally: Este código é executado SEMPRE, quer ocorram exceções ou não.
            // É o local ideal para limpar recursos.

            // Informa o BingoServer para remover este jogador da lista de jogadores ativos.
            bingoServer.removePlayer(this);

            closeResources();
            System.out.println("ClientHandler: Handler para " + clientIdentifierForLogs + " terminado e recursos fechados.");
        }
    }

    // Envia uma mensagem para o cliente associado a este handler.
    public void sendMessage(String message) {
        if (out != null && !clientSocket.isClosed() && !out.checkError()) {

            out.println(message);

        } else {
            System.err.println("ClientHandler: Não foi possível enviar mensagem para "
                    + (playerName != null ? playerName : clientSocket.getRemoteSocketAddress())
                    + ". Stream de saída nulo, socket fechado ou erro no stream.");
        }
    }

    //Método privado para fechar os recursos (streams e socket) de forma segura.
    private void closeResources() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            System.err.println("ClientHandler: Erro ao fechar input stream: " + e.getMessage());
        }
        if (out != null) {
            out.close();
        }
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("ClientHandler: Erro ao fechar client socket: " + e.getMessage());
        }
    }

    // Usados pelo BingoServer para obter informações sobre este jogador ou para definir dados.
    public String getPlayerName() {
        return playerName;
    }

    public String getPlayerCardId() {
        return playerCardId;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setPlayerCardId(String playerCardId) {
        this.playerCardId = playerCardId;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public void setReady(boolean ready) {
        this.isReady = ready;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }
}
