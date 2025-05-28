package com.mycompany.sockets;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

public class BingoClient extends JFrame {

    // Componentes da interface
    private JTextField nameField;                      // Campo para inserir o nome do jogador
    private JLabel nameLabel;                          // Rótulo para mostrar o nome após registo
    private JButton readyButton, lineButton, bingoButton; // Botões principais do jogo
    private JLabel statusLabel, cardIdLabel;           // Rótulos para mostrar estado e ID do cartão
    private JPanel cardPanel, drawnNumbersPanel;       // Painéis para o cartão e números sorteados
    private JButton[] cardButtons = new JButton[25];   // Botões que representam as células do cartão 5x5
    private String cardId;                             // Identificador único do cartão (UUID)
    private java.util.List<JLabel> drawnNumberLabels = new ArrayList<>(); // Números sorteados recebidos
    private JPanel topPanel;
    private JPanel namePanel;
    private ClienteSocket clienteSocket;               // Comunicação com o servidor

    public BingoClient() {
        // Configuração base da janela
        setTitle("Cliente Bingo ESTGA");
        setSize(900, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Centra a janela
        setLayout(new BorderLayout());

        // ---------- TOPO ----------
        topPanel = new JPanel(new BorderLayout());

        // Painel com o nome
        namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nameField = new JTextField(15);
        namePanel.add(new JLabel("Name: "));
        namePanel.add(nameField);
        topPanel.add(namePanel, BorderLayout.WEST);

        // Geração do ID do cartão (parte visível)
        cardId = UUID.randomUUID().toString().substring(0, 8); // 8 caracteres únicos
        cardIdLabel = new JLabel("Card ID: " + cardId);
        JPanel idPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        idPanel.add(cardIdLabel);
        topPanel.add(idPanel, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        // ---------- CARTÃO DE BINGO ----------
        cardPanel = new JPanel(new GridLayout(5, 5, 5, 5));
        for (int i = 0; i < 25; i++) {
            JButton cell = new JButton("--"); // Placeholder
            cell.setFont(new Font("Arial", Font.PLAIN, 16));
            cell.setEnabled(false); // Só será ativado depois
            cardButtons[i] = cell;
            cardPanel.add(cell);
        }
        add(cardPanel, BorderLayout.CENTER);

        // ---------- BOTÕES EM BAIXO ----------
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonsPanel = new JPanel();

        readyButton = new JButton("Pronto para iniciar");
        lineButton = new JButton("Linha");
        bingoButton = new JButton("Bingo");

        // Linha e Bingo só são ativados depois de clicar "Pronto"
        lineButton.setEnabled(false);
        bingoButton.setEnabled(false);

        buttonsPanel.add(readyButton);
        buttonsPanel.add(lineButton);
        buttonsPanel.add(bingoButton);

        // Status informativo
        statusLabel = new JLabel("Status: Waiting for login...");
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        bottomPanel.add(buttonsPanel, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // ---------- NÚMEROS SORTEADOS ----------
        drawnNumbersPanel = new JPanel();
        drawnNumbersPanel.setLayout(new BoxLayout(drawnNumbersPanel, BoxLayout.Y_AXIS));
        drawnNumbersPanel.setBorder(BorderFactory.createTitledBorder("Drawn Numbers"));

        JScrollPane scrollPane = new JScrollPane(drawnNumbersPanel);
        scrollPane.setPreferredSize(new Dimension(150, 0));
        add(scrollPane, BorderLayout.EAST);

        // ---------- LÓGICA DO BOTÃO "PRONTO" ----------
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                validateName();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                validateName();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                validateName();
            }

            // Só ativa o botão "Pronto" se o nome for preenchido
            private void validateName() {
                readyButton.setEnabled(!nameField.getText().trim().isEmpty());
            }
        });
        readyButton.setEnabled(false); // Inicialmente desativado

        // Ação ao clicar "Pronto para iniciar"
        readyButton.addActionListener(e -> {
            try {
                // Conecta ao servidor
                clienteSocket = new ClienteSocket("localhost", 12345, this);

                // Envia nome e ID
                String nome = nameField.getText().trim();
                clienteSocket.enviarMensagem("NOME:" + nome + ";" + cardId);

                // Interface: oculta botão e prepara cartão
                readyButton.setVisible(false);
                generateCard(); // Pode ser substituído por preencherCartaoComNumeros(...) do servidor
                statusLabel.setText("Status: Waiting for other players...");
                lineButton.setEnabled(true);
                bingoButton.setEnabled(true);
                replaceNameField(); // Substitui JTextField por JLabel
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Erro ao ligar ao servidor.");
                statusLabel.setText("Status: Erro de ligação ao servidor.");
            }
        });

        // Botões de linha e bingo enviam pedido ao servidor
        lineButton.addActionListener(e -> {
            if (clienteSocket != null) {
                clienteSocket.enviarMensagem("LINHA:" + cardId);
                updateStatus("Linha solicitada...");
                // Opcional: desativar para evitar spam
                lineButton.setEnabled(false);
            }
        });

        bingoButton.addActionListener(e -> {
            if (clienteSocket != null) {
                clienteSocket.enviarMensagem("BINGO:" + cardId);
                updateStatus("Bingo solicitado...");
                // Opcional: desativar para evitar spam
                bingoButton.setEnabled(false);
            }
        });

        setVisible(true); // Mostra a janela
    }

    /*Substitui o campo de texto pelo nome fixo depois do envio.*/
    private void replaceNameField() {
        String name = nameField.getText();
        namePanel.removeAll();
        nameLabel = new JLabel("Name: " + name);
        namePanel.add(nameLabel);
        namePanel.revalidate();
        namePanel.repaint();
    }

    /* Este método pode ser removido se o cartão for recebido do servidor.*/
    private void generateCard() {
        Set<Integer> numbers = new LinkedHashSet<>();
        Random rand = new Random();
        while (numbers.size() < 25) {
            numbers.add(rand.nextInt(75) + 1);
        }

        Iterator<Integer> it = numbers.iterator();
        for (int i = 0; i < 25; i++) {
            int number = it.next();
            JButton button = cardButtons[i];
            button.setText(String.valueOf(number));
            button.setEnabled(true);
            button.setBackground(null);

            // Cada botão pode ser marcado/desmarcado
            button.addActionListener(new ActionListener() {
                boolean marked = false;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (!marked) {
                        button.setBackground(Color.GREEN);
                        marked = true;
                    } else {
                        button.setBackground(null);
                        marked = false;
                    }
                }
            });
        }
    }

    /* mais recente aparece a negrito*/
    public void addDrawnNumber(int number) {
        JLabel newLabel = new JLabel(String.valueOf(number));
        newLabel.setFont(new Font("Arial", Font.BOLD, 18));

        // Os anteriores voltam ao normal
        for (JLabel label : drawnNumberLabels) {
            label.setFont(new Font("Arial", Font.PLAIN, 16));
        }

        drawnNumberLabels.add(newLabel);
        drawnNumbersPanel.add(newLabel);
        drawnNumbersPanel.revalidate();
        drawnNumbersPanel.repaint();
    }

    /* Atualiza o texto do statusLabel com uma mensagem recebida do servidor*/
    public void updateStatus(String mensagem) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Status: " + mensagem);

            // Se a mensagem for de confirmação de linha ou bingo, pode atualizar botões
            if (mensagem.toLowerCase().contains("linha feita") || mensagem.toLowerCase().contains("linha correta")) {
                lineButton.setEnabled(false);
            }
            if (mensagem.toLowerCase().contains("bingo feito") || mensagem.toLowerCase().contains("parabéns")) {
                bingoButton.setEnabled(false);
                lineButton.setEnabled(false);
                // Opcional: bloquear interação no cartão se quiser
                for (JButton btn : cardButtons) {
                    btn.setEnabled(false);
                }
            }
            // Se for mensagem de erro ou para tentar novamente, reabilita os botões
            if (mensagem.toLowerCase().contains("tenta novamente") || mensagem.toLowerCase().contains("erro")) {
                lineButton.setEnabled(true);
                bingoButton.setEnabled(true);
            }
        });
    }

    /* Método principal: cria e executa a interface*/
    public static void main(String[] args) {
        SwingUtilities.invokeLater(BingoClient::new);
    }


    public void setCardId(String cardId) {
        this.cardId = cardId;
        cardIdLabel.setText("Cartão: " + cardId);
    }

    public void preencherCartaoComNumeros(List<Integer> numeros) {
        for (int i = 0; i < 25; i++) {
            int numero = numeros.get(i);
            JButton botao = cardButtons[i];
            botao.setText(String.valueOf(numero));
            botao.setEnabled(true); // Garante que está clicável
            botao.setBackground(null); // Reset visual
        }
    }

}
