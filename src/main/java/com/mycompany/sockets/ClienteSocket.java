package com.mycompany.sockets; // Declaração do pacote

import java.io.*;
import java.net.*;

// Classe responsável pela comunicação entre o cliente e o servidor
public class ClienteSocket {

    private Socket socket;                  // Socket de ligação ao servidor
    private BufferedReader in;             // Para ler mensagens do servidor
    private PrintWriter out;               // Para enviar mensagens ao servidor
    private BingoClient ui;                // Referência à interface gráfica (BingoClient)

    /*
     Construtor da classe ClienteSocket.
     Estabelece a ligação ao servidor e inicia a escuta de mensagens.
     
     host endereço IP do servidor (ex: "localhost")
     port porta do servidor (ex: 12345)
     ui instância da interface gráfica (BingoClient)
     */
    public ClienteSocket(String host, int port, BingoClient ui) throws IOException {
        this.ui = ui;
        // Cria o socket e liga ao servidor
        this.socket = new Socket(host, port);
        // Inicializa os fluxos de entrada e saída de dados
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true); // 'true' ativa autoflush
        // Inicia uma thread separada para ouvir mensagens do servidor
        new Thread(this::ouvirServidor).start();
    }

    /*
     Envia uma mensagem ao servidor.
     msg mensagem em formato texto (ex: "NOME:Joao")
     */
    public void enviarMensagem(String msg) {
        out.println(msg); // Envia a mensagem para o servidor
    }

    // Método privado que corre numa thread separada para ouvir continuamente o servidor.
    private void ouvirServidor() {
        try {
            String linha;
            // Loop contínuo: lê mensagens do servidor até a ligação ser fechada
            while ((linha = in.readLine()) != null) {
                System.out.println("Servidor: " + linha); // Para debug
                tratarMensagem(linha); // Processa a mensagem recebida
            }
        } catch (IOException e) {
            e.printStackTrace(); // Imprime o erro se a ligação falhar
            ui.updateStatus("Ligação ao servidor perdida.");// Mostra erro na interface
        } finally {
            try {
                // Garante que o socket é fechado quando a ligação termina
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    //Analisa e trata a mensagem recebida do servidor msg mensagem recebida
    private void tratarMensagem(String msg) {
        // Trata mensagens no formato "NUM:<número>"
        if (msg.startsWith("NUM:")) {
            try {
                int numero = Integer.parseInt(msg.substring(4)); // Extrai o número depois de "NUM:"
                ui.addDrawnNumber(numero); // Adiciona o número à interface (zona direita)
            } catch (NumberFormatException e) {
                System.err.println("Erro ao interpretar número sorteado: " + msg);
            }

            // Trata mensagens no formato "DRAWN_NUMBER:<número>" (novo formato enviado pelo servidor)
        } else if (msg.startsWith("DRAWN_NUMBER:")) {
            try {
                // Extrai o número a partir do índice após o prefixo "DRAWN_NUMBER:"
                int numero = Integer.parseInt(msg.substring("DRAWN_NUMBER:".length()).trim());
                ui.addDrawnNumber(numero); // Adiciona à lista de números sorteados na interface
            } catch (NumberFormatException e) {
                System.err.println("Erro ao interpretar DRAWN_NUMBER: " + msg);
            }

            // Trata mensagem especial "GAME_STARTING", que indica o início do jogo
        } else if (msg.equals("GAME_STARTING")) {
            ui.updateStatus("O jogo vai começar!"); // Atualiza o label de estado no fundo

            // Mensagem geral enviada com "MSG:<conteúdo>"
        } else if (msg.startsWith("MSG:")) {
            String conteudo = msg.substring(4); // Extrai o texto
            ui.updateStatus(conteudo); // Atualiza o label de estado com a mensagem

            // O jogador ganhou (mensagem "WIN")
        } else if (msg.equals("WIN")) {
            ui.updateStatus("Parabéns! Fizeste Bingo!"); // Mostra vitória

            // O jogador perdeu (mensagem "LOSE")
        } else if (msg.equals("LOSE")) {
            ui.updateStatus("Ainda não foi desta. Tenta novamente."); // Mostra falha

            // Erros do servidor: "ERRO:<mensagem>"
        } else if (msg.startsWith("ERRO:")) {
            ui.updateStatus("Erro: " + msg.substring(5)); // Mostra o erro na interface

            // Informações do servidor: "INFO:<mensagem informativa>"
        } else if (msg.startsWith("INFO:")) {
            ui.updateStatus(msg.substring(5)); // Exibe informação no status

            // Define o ID do cartão atribuído pelo servidor: "CARD_ID:<id>"
        } else if (msg.startsWith("CARD_ID:")) {
            String cardId = msg.substring(8); // Extrai o ID
            ui.setCardId(cardId); // Define o ID na interface (canto superior direito)

            // Recebe os números do cartão: "CARD_DATA:1,2,3,..."
        } else if (msg.startsWith("CARD_DATA:")) {
            String[] partes = msg.substring(10).split(","); // Divide os números por vírgulas
            java.util.List<Integer> numeros = new java.util.ArrayList<>();
            for (String parte : partes) {
                numeros.add(Integer.parseInt(parte)); // Converte cada número para inteiro
            }
            ui.preencherCartaoComNumeros(numeros); // Preenche os botões do cartão na interface

            // Mensagem a informar que está à espera de mais jogadores
        } else if (msg.startsWith("WAITING_FOR_PLAYERS:")) {
            ui.updateStatus(msg); // Mostra no status

        } else if (msg.startsWith("VALIDATION_LINE_FAIL:")) {
            ui.updateStatus(msg.substring("VALIDATION_LINE_FAIL:".length()).trim());
            ui.reabilitarBotaoLinha(); // <-- método que tu defines para voltar a ativar o botão
        } else if (msg.startsWith("VALIDATION_LINE_OK")) {
            ui.updateStatus("Linha validada com sucesso!");
        } else if (msg.startsWith("VALIDATION_BINGO_FAIL:")) {
            ui.updateStatus(msg.substring("VALIDATION_BINGO_FAIL:".length()).trim());
            ui.reabilitarBotaoBingo();
        } else if (msg.startsWith("VALIDATION_BINGO_OK")) {
            ui.updateStatus("Bingo validado com sucesso!");
        } else if (msg.startsWith("LINE_ANNOUNCEMENT:")) {
            String conteudo = msg.substring("LINE_ANNOUNCEMENT:".length()).trim();
            ui.updateStatus(conteudo);
        } // Se nenhuma das opções anteriores corresponder, é uma mensagem desconhecida
        else {
            System.out.println("Mensagem não reconhecida: " + msg); // Debug no terminal
        }
    }

}
