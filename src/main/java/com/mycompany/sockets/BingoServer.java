package com.mycompany.sockets;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashSet; // verificar se todos os números do cartão foram sorteados (em checkBingo)
import java.util.Set;     // Para o mesmo propósito acima
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BingoServer {

    private final int port;                                 // Porta onde o servidor vai escutar
    private static final int MIN_PLAYERS_TO_START = 2;
    private static final int MAX_PLAYERS = 10;
    private static final int DRAW_INTERVAL_SECONDS = 5;

    private ServerSocket serverSocket;
    private final List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>(); // Lista de todos os handlers de clientes conectados.

    private final Map<String, BingoCard> playerCards = new ConcurrentHashMap<>();   // Mapeia ID do cartão (String) para o objeto BingoCard.
    private final Map<String, ClientHandler> cardIdToHandler = new ConcurrentHashMap<>(); // Mapeia ID do cartão para o ClientHandler respetivo.

    private final AtomicInteger nextCardIdSuffix = new AtomicInteger(1); // Para gerar IDs de cartão únicos de forma thread-safe
    private volatile boolean gameInProgress = false;
    private final List<Integer> drawnNumbers = Collections.synchronizedList(new ArrayList<>());

    private ScheduledExecutorService numberDrawingScheduler;
    private volatile boolean bingoClaimedThisGame = false; // Indica se alguém já fez bingo neste jogo.

    public BingoServer(int port) {
        this.port = port;
    }

    //Inicia o servidor: abre o ServerSocket e começa a aceitar conexões de clientes.
    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Servidor de Bingo iniciado na porta " + port);
            System.out.println("A aguardar jogadores... Mín: " + MIN_PLAYERS_TO_START + ", Máx: " + MAX_PLAYERS);

            numberDrawingScheduler = Executors.newSingleThreadScheduledExecutor();

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Novo cliente conectado: " + clientSocket.getRemoteSocketAddress());

                    // Verifica se o servidor não está cheio e se não há um jogo em progresso
                    // (novos jogadores não podem entrar a meio de um jogo).
                    if (clientHandlers.size() < MAX_PLAYERS && !gameInProgress) {
                        ClientHandler newClientHandler = new ClientHandler(clientSocket, this);

                        // O registo do jogador (com nome e atribuição de cartão) ocorrerá dentro do método run() do ClientHandler.
                        new Thread(newClientHandler).start();
                    } else {
                        // Servidor cheio ou jogo em progresso. Rejeita o cliente.
                        String rejectReason = gameInProgress ? "Jogo já em progresso." : "Servidor cheio.";
                        System.out.println(rejectReason + " Rejeitando ligação de " + clientSocket.getRemoteSocketAddress());
                        // Envia uma mensagem de erro ao cliente e fecha a ligação.
                        try (PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true)) {
                            tempOut.println("ERROR:" + rejectReason + " Por favor, tente mais tarde.");
                        }
                        clientSocket.close(); // Fecha o socket do cliente rejeitado.
                    }
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        // Se o serverSocket foi fechado
                        System.out.println("ServerSocket fechado. A parar de aceitar novos clientes.");
                        break;
                    }
                    System.err.println("Erro ao aceitar ligação de cliente: " + e.getMessage());

                }
            }
        } catch (IOException e) {
            System.err.println("Não foi possível iniciar o servidor na porta " + port + ": " + e.getMessage());
            // Se o servidor não conseguir sequer abrir o ServerSocket, não há muito a fazer.
        } finally {

            stopServer();
        }
    }

    
     /* Método chamado pelo ClientHandler quando um jogador envia a mensagem de
      registo. Este método é synchronized para garantir que o registo de
      múltiplos jogadores em simultâneo não causa problemas de concorrência.
     */
          
    public synchronized void registerPlayer(ClientHandler handler) {
        String playerName = handler.getPlayerName();

        // Verificações pré-registo:
        if (gameInProgress) {
            handler.sendMessage("ERROR:Jogo já em progresso. Por favor, aguarde pelo próximo jogo.");
            closeClientSocketGracefully(handler.getClientSocket(), "jogador tardio " + playerName);
            return;
        }
        if (clientHandlers.size() >= MAX_PLAYERS) {
            handler.sendMessage("ERROR:Servidor cheio.");
            closeClientSocketGracefully(handler.getClientSocket(), "jogador com servidor cheio " + playerName);
            return;
        }
        // Verifica se o nome do jogador já está em uso.
        for (ClientHandler ch : clientHandlers) {
            if (ch.getPlayerName() != null && ch.getPlayerName().equalsIgnoreCase(playerName)) {
                handler.sendMessage("ERROR:O nome '" + playerName + "' já está em uso. Por favor, escolha outro.");
                closeClientSocketGracefully(handler.getClientSocket(), "jogador com nome duplicado " + playerName);
                return;
            }
        }

        // Se passou todas as verificações, regista o jogador:
        String cardId = "CARTAO-" + nextCardIdSuffix.getAndIncrement(); // Gera um ID único para o cartão.
        BingoCard newCard = new BingoCard(cardId);

        handler.setPlayerCardId(cardId); 

        // Adiciona o handler e os dados do jogador às estruturas de dados do servidor.
        clientHandlers.add(handler);
        playerCards.put(cardId, newCard);
        cardIdToHandler.put(cardId, handler);

        System.out.println("Jogador " + playerName + " registado com Cartão ID: " + cardId);
        System.out.println("Cartão para " + playerName + ":\n" + newCard.toString()); // Log do cartão no servidor para depuração.

        // Envia o ID do cartão e os números do cartão de volta para o cliente.
        handler.sendMessage("CARD_ID:" + cardId);
        handler.sendMessage("CARD_DATA:" + newCard.getNumbersAsCommaSeparatedString());

        
        broadcastPlayerCountStatus();
    }

    /*
      Método chamado pelo ClientHandler quando um jogador envia a mensagem
      "READY". Synchronized para evitar problemas de concorrência ao verificar
      e iniciar o jogo.
    */
    public synchronized void playerIsReady(ClientHandler handler) {
        if (!clientHandlers.contains(handler) || gameInProgress) {
            // Ignora se o jogador não está registado ou se o jogo já começou.
            System.out.println("Jogador " + handler.getPlayerName() + " tentou ficar PRONTO mas não é permitido (não registado ou jogo em progresso).");
            return;
        }

        System.out.println("Jogador " + handler.getPlayerName() + " está PRONTO.");
        
        broadcastMessageToAll("INFO:" + handler.getPlayerName() + " está pronto.", null);
        broadcastPlayerCountStatus(); // Atualiza a contagem de jogadores prontos.

        // Verifica se todos os jogadores necessários estão prontos para iniciar o jogo.
        checkIfAllPlayersAreReadyAndStartGame();
    }

    /*
      Verifica se o número mínimo de jogadores foi atingido e se todos os
      jogadores conectados estão prontos. Se sim, inicia o jogo. Este método é
      chamado após um jogador ficar "Pronto" ou após um jogador sair (para
      reavaliar).
     */
    private synchronized void checkIfAllPlayersAreReadyAndStartGame() {
        if (gameInProgress) {
            return;
        }
        if (clientHandlers.size() < MIN_PLAYERS_TO_START) {
            
            return;
        }

        // Verifica se todos os jogadores na lista clientHandlers estão prontos.
        for (ClientHandler ch : clientHandlers) {
            if (!ch.isReady()) {
                // Pelo menos um jogador ainda não está pronto.
                return;
            }
        }        
        startGame();
    }

    /*
      Inicia um novo jogo de bingo. Define o estado do jogo, limpa dados de
      jogos anteriores e começa o sorteio de números.
     */
    private synchronized void startGame() {
        if (gameInProgress) {
            return; 
        }
        System.out.println("Todos os " + clientHandlers.size() + " jogadores estão prontos. A iniciar o jogo!");
        gameInProgress = true;
        bingoClaimedThisGame = false; // Ninguém fez bingo ainda neste jogo.
        drawnNumbers.clear();         

        broadcastMessageToAll("GAME_STARTING", null); // Informa todos os clientes que o jogo começou.

        // (Re)cria o scheduler se, por alguma razão, foi parado (ex: após um jogo anterior).
        if (numberDrawingScheduler == null || numberDrawingScheduler.isShutdown()) {
            numberDrawingScheduler = Executors.newSingleThreadScheduledExecutor();
        }

        
        numberDrawingScheduler.scheduleAtFixedRate(
                this::drawAndBroadcastNumber, // O método a ser executado (referência de método)
                DRAW_INTERVAL_SECONDS, // Atraso inicial antes da primeira execução
                DRAW_INTERVAL_SECONDS, // Intervalo entre execuções subsequentes
                TimeUnit.SECONDS // Unidade de tempo para o atraso e intervalo
        );
    }

    /*
      Sorteia um novo número de bingo (que ainda não tenha sido sorteado) e
      envia-o para todos os clientes conectados. Este método é chamado pelo
      numberDrawingScheduler.
     */
    private synchronized void drawAndBroadcastNumber() {
        if (!gameInProgress || bingoClaimedThisGame) {
            // Se o jogo não estiver em progresso ou se alguém já fez bingo, para de sortear.
             em endGame().
            return;
        }

        if (drawnNumbers.size() >= BingoCard.MAX_NUMBER_VALUE) {
            // Todos os números possíveis (1-99) já foram sorteados.
            System.out.println("Todos os números possíveis já foram sorteados.");
            broadcastMessageToAll("INFO:Todos os números foram sorteados. Fim do jogo. Nenhum vencedor de bingo.", null);
            endGame(null); // Termina o jogo, indicando que não houve vencedor de bingo.
            return;
        }

        Random random = new Random();
        int newNumber;
        do {
            // Sorteia um número dentro do intervalo válido (1-99).
            newNumber = random.nextInt(BingoCard.MAX_NUMBER_VALUE - BingoCard.MIN_NUMBER_VALUE + 1) + BingoCard.MIN_NUMBER_VALUE;
        } while (drawnNumbers.contains(newNumber)); // Continua a sortear até encontrar um número que ainda não saiu.

        drawnNumbers.add(newNumber); // Adiciona o novo número à lista de sorteados.
        // Collections.sort(drawnNumbers); // Opcional: manter a lista ordenada (bom para depuração).
        System.out.println("Número sorteado: " + newNumber + " (Total sorteados: " + drawnNumbers.size() + ")");

        // Envia o número sorteado para todos os clientes.
        broadcastMessageToAll("DRAWN_NUMBER:" + newNumber, null);
    }

    /*
      Lida com um pedido de "Linha" de um jogador. Chama o método checkLine()
      do BingoCard do jogador.   
    */
    
    public synchronized void handleClaimLine(ClientHandler claimingHandler) {
        if (!gameInProgress || bingoClaimedThisGame) {
            claimingHandler.sendMessage("VALIDATION_LINE_FAIL:O jogo não está ativo ou o bingo já foi reclamado.");
            return;
        }

        String cardId = claimingHandler.getPlayerCardId();
        BingoCard card = playerCards.get(cardId);

        if (card == null) {
            
            claimingHandler.sendMessage("ERROR:Cartão não encontrado para o pedido (ID: " + cardId + ").");
            System.err.println("Erro em handleClaimLine: Cartão não encontrado para ID " + cardId + " do jogador " + claimingHandler.getPlayerName());
            return;
        }

        
        // se a lista for modificada enquanto checkLine() está a ser executado (embora drawnNumbers seja sincronizada).
        boolean lineValid = card.checkLine(new ArrayList<>(drawnNumbers));

        if (lineValid) {
            System.out.println("Pedido de LINHA do jogador " + claimingHandler.getPlayerName() + " (Cartão " + cardId + ") é VÁLIDO.");
            claimingHandler.sendMessage("VALIDATION_LINE_OK");
            broadcastMessageToAll("LINE_ANNOUNCEMENT:Linha feita pelo utilizador " + claimingHandler.getPlayerName() + "!", null);
            // O jogo continua para bingo, mesmo após uma linha.
        } else {
            System.out.println("Pedido de LINHA do jogador " + claimingHandler.getPlayerName() + " (Cartão " + cardId + ") é INVÁLIDO.");
            claimingHandler.sendMessage("VALIDATION_LINE_FAIL:A sua linha não é válida com os números atuais.");
        }
    }

    /*
      Lida com um pedido de "Bingo" de um jogador. Chama o método checkBingo()
      do BingoCard do jogador. Se o bingo for válido, termina o jogo.    
     */
    public synchronized void handleClaimBingo(ClientHandler claimingHandler) {
        if (!gameInProgress || bingoClaimedThisGame) {
            claimingHandler.sendMessage("VALIDATION_BINGO_FAIL:O jogo não está ativo ou o bingo já foi reclamado.");
            return;
        }

        String cardId = claimingHandler.getPlayerCardId();
        BingoCard card = playerCards.get(cardId);

        if (card == null) {
            claimingHandler.sendMessage("ERROR:Cartão não encontrado para o pedido (ID: " + cardId + ").");
            System.err.println("Erro em handleClaimBingo: Cartão não encontrado para ID " + cardId + " do jogador " + claimingHandler.getPlayerName());
            return;
        }

        boolean bingoValid = card.checkBingo(new ArrayList<>(drawnNumbers));

        if (bingoValid) {
            System.out.println("Pedido de BINGO do jogador " + claimingHandler.getPlayerName() + " (Cartão " + cardId + ") é VÁLIDO!");
            bingoClaimedThisGame = true; // Marca que o bingo foi feito neste jogo.

            claimingHandler.sendMessage("VALIDATION_BINGO_OK");
            claimingHandler.sendMessage("BINGO_WINNER:Parabéns! Você fez BINGO!");

            // Informa os outros jogadores que houve um vencedor.
            for (ClientHandler handler : clientHandlers) {
                if (handler != claimingHandler) {
                    handler.sendMessage("BINGO_LOSER:Bingo feito por " + claimingHandler.getPlayerName() + ". Mais sorte para a próxima!");
                }
            }
            endGame(claimingHandler.getPlayerName()); // Termina o jogo, passando o nome do vencedor.
        } else {
            System.out.println("Pedido de BINGO do jogador " + claimingHandler.getPlayerName() + " (Cartão " + cardId + ") é INVÁLIDO.");
            claimingHandler.sendMessage("VALIDATION_BINGO_FAIL:O seu bingo não é válido com os números atuais.");
        }
    }

    /*
      Termina o jogo atual. Para o sorteio de números, informa os jogadores e
      prepara o servidor para um novo jogo.
     */
    private synchronized void endGame(String winnerName) {
        System.out.println("A terminar o jogo. Vencedor: " + (winnerName != null ? winnerName : "Nenhum (ou todos os números sorteados)"));
        gameInProgress = false; 

        // Para o scheduler de sorteio de números.
        if (numberDrawingScheduler != null && !numberDrawingScheduler.isShutdown()) {
            numberDrawingScheduler.shutdown(); // Impede novas tarefas de serem submetidas.
            try {
                // Espera um pouco para as tarefas atuais terminarem.
                if (!numberDrawingScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    numberDrawingScheduler.shutdownNow(); 
                }
            } catch (InterruptedException e) {
                numberDrawingScheduler.shutdownNow(); 
                Thread.currentThread().interrupt();   // Preserva o estado de interrupção.
            }
        }
        // O scheduler será (re)criado no próximo startGame().

        String endMessage = "INFO:O jogo terminou. "
                + (winnerName != null ? "Vencedor: " + winnerName + "." : "Nenhum vencedor de bingo desta vez.")
                + " Para jogar novamente, certifique-se que o seu nome está preenchido e clique em 'Pronto'.";
        broadcastMessageToAll(endMessage, null);

        // Prepara os jogadores para um novo jogo (se permanecerem conectados):
        // - Reseta o estado de "Pronto" deles.
        // - Mantém os seus cartões.
        for (ClientHandler ch : clientHandlers) {
            ch.setReady(false); 
        }
        
        System.out.println("Servidor pronto para um novo jogo. Os jogadores precisam de se marcar como 'Pronto' novamente.");
        broadcastPlayerCountStatus(); // Atualiza o status para mostrar 0/N prontos.
    }

    /*
      Remove um jogador do servidor (ex: quando se desconecta).
      Synchronized para proteger o acesso às listas partilhadas.     
     */
    public synchronized void removePlayer(ClientHandler handler) {
        if (handler == null) {
            return;
        }

        String playerName = handler.getPlayerName(); // Pode ser null se o registo falhou ou o handler foi removido antes.
        String cardId = handler.getPlayerCardId();

        boolean removed = clientHandlers.remove(handler); // Tenta remover da lista principal.

        
        
        if (cardId != null) {
            playerCards.remove(cardId);
            cardIdToHandler.remove(cardId);
        }

        if (removed) {
            System.out.println("Jogador " + (playerName != null ? playerName : "Cliente não registado")
                    + (cardId != null ? " (Cartão " + cardId + ")" : "")
                    + " desconectou-se ou foi removido.");
            // Informa os outros jogadores que alguém saiu.
            broadcastMessageToAll("INFO:O jogador " + (playerName != null ? playerName : "Um jogador") + " saiu do jogo.", handler); // Exclui o próprio handler que está a ser removido.
            broadcastPlayerCountStatus(); 

            // Lógica adicional se a saída de um jogador afeta o jogo:
            if (gameInProgress && clientHandlers.size() < MIN_PLAYERS_TO_START) {
                
                System.out.println("Não há jogadores suficientes para continuar (" + clientHandlers.size() + "/" + MIN_PLAYERS_TO_START + "). A terminar o jogo.");
                broadcastMessageToAll("INFO:Não há jogadores suficientes para continuar. O jogo vai terminar.", null);
                endGame(null); // Termina o jogo sem vencedor.
            } else if (!gameInProgress && clientHandlers.size() >= MIN_PLAYERS_TO_START) {
                
                checkIfAllPlayersAreReadyAndStartGame();
            }
        }
    }

    /*
      Envia uma mensagem para todos os clientes conectados.     
      */
    public void broadcastMessageToAll(String message, ClientHandler excludeHandler) {
        
        for (ClientHandler handler : clientHandlers) {
            if (handler != excludeHandler) {
                handler.sendMessage(message);
            }
        }
    }

    /*
      Difunde o estado atual do número de jogadores e quantos estão prontos.
     */
    private void broadcastPlayerCountStatus() {
        long readyCount = clientHandlers.stream().filter(ClientHandler::isReady).count();
        int totalPlayers = clientHandlers.size();
        String statusMsg;

        if (gameInProgress) {
            statusMsg = "INFO:Jogo em progresso. Jogadores: " + totalPlayers + ". Sorteados: " + drawnNumbers.size();
        } else {
            // Exemplo: WAITING_FOR_PLAYERS:1/2 (Min 2 para iniciar, Max 10)
            statusMsg = "WAITING_FOR_PLAYERS:" + readyCount + "/" + totalPlayers
                    + " (Min " + MIN_PLAYERS_TO_START + " para iniciar, Max " + MAX_PLAYERS + ")";
        }
        broadcastMessageToAll(statusMsg, null);
        System.out.println("Atualização de Estado: " + statusMsg); // Log no servidor.
    }

    /*
      Para o servidor de forma organizada, fechando sockets e parando threads.
      Este método pode ser chamado pelo shutdown hook ou se ocorrer um erro fatal.
     */
    public synchronized void stopServer() {
        System.out.println("A parar o Servidor de Bingo...");
        gameInProgress = false; // Garante que novas lógicas de jogo não iniciem.

        if (numberDrawingScheduler != null && !numberDrawingScheduler.isShutdown()) {
            numberDrawingScheduler.shutdownNow(); // Tenta parar todas as tarefas de sorteio imediatamente.
        }

        broadcastMessageToAll("SERVER_SHUTDOWN:O servidor está a desligar.", null);

        
        // É importante iterar sobre uma cópia da lista se a remoção de um handler (no seu finally)
        // modificar a lista original clientHandlers. CopyOnWriteArrayList lida bem com isto.
        for (ClientHandler handler : clientHandlers) {
            closeClientSocketGracefully(handler.getClientSocket(), "jogador " + handler.getPlayerName() + " durante o shutdown do servidor");
            
        }
        clientHandlers.clear(); // Limpa a lista principal após fechar os sockets.
        playerCards.clear();
        cardIdToHandler.clear();

        // Fecha o ServerSocket principal.
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("ServerSocket principal fechado.");
            } catch (IOException e) {
                System.err.println("Erro ao fechar o ServerSocket principal: " + e.getMessage());
            }
        }
        System.out.println("Servidor de Bingo parado.");
    }

    /*
      Método auxiliar para fechar um socket de cliente de forma mais
      controlada, com logging.
     */
    private void closeClientSocketGracefully(Socket socket, String context) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket do " + context + ": " + e.getMessage());
            }
        }
    }

    /*
      Ponto de entrada principal para iniciar o servidor.
     */
    public static void main(String[] args) {
        int serverPort = 12345;
        if (args.length > 0) {
            try {
                serverPort = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Número de porta inválido: " + args[0] + ". A usar porta padrão " + serverPort);
            }
        }

        BingoServer server = new BingoServer(serverPort);

        // Adiciona um "shutdown hook": uma thread que é executada quando a JVM está a terminar
        // (ex: se o utilizador pressionar Ctrl+C no console, ou se o sistema enviar um sinal de término).
        // Isto permite que o servidor tente parar de forma organizada.
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
        // Equivalente a:
        // Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stopServer()));

        server.startServer(); // Este método contém o loop principal de aceitação e só retorna quando o servidor para.
    }
}
