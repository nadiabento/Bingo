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
        }
    }

    /*
      Analisa e trata a mensagem recebida do servidor.
      msg mensagem recebida
     */
    private void tratarMensagem(String msg) {
        // Exemplo: "NUM:45" — número sorteado
        if (msg.startsWith("NUM:")) {
            int numero = Integer.parseInt(msg.substring(4)); // extrai número após "NUM:"
            ui.addDrawnNumber(numero); // atualiza a interface gráfica

        // Exemplo: "MSG:Linha feita por Joana"
        } else if (msg.startsWith("MSG:")) {
            String conteudo = msg.substring(4); // extrai mensagem após "MSG:"
            ui.updateStatus(conteudo); // mostra no status da interface

        // Exemplo: "WIN" — este cliente fez bingo
        } else if (msg.equals("WIN")) {
            ui.updateStatus("Parabéns! Fizeste Bingo!");

        // Exemplo: "LOSE" — outro jogador fez bingo
        } else if (msg.equals("LOSE")) {
            ui.updateStatus("Ainda não foi desta. Tenta novamente.");
        }
    }
}
