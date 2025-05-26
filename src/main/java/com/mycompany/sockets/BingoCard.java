package com.mycompany.sockets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

//Class responsável por representar um único cartão. Gera os nº para o cartão
//e fornece formas de aceder a esses Nºs.
public class BingoCard {

    // define as características do cartão de bingo.
    private static final int CARD_SIZE = 5;
    private static final int NUMBERS_PER_CARD = CARD_SIZE * CARD_SIZE; // Total de números no cartão (25)
    public static final int MAX_NUMBER_VALUE = 99;
    public static final int MIN_NUMBER_VALUE = 1;

    // Cada objeto BingoCard terá estas variáveis.
    private final String id;
    private final int[][] numbers = new int[CARD_SIZE][CARD_SIZE];
    private final List<Integer> linearNumbers = new ArrayList<>(NUMBERS_PER_CARD);

    public BingoCard(String id) {
        this.id = id;
        generateNumbers();
    }

    private void generateNumbers() {

        Set<Integer> uniqueNumbers = new LinkedHashSet<>();
        Random random = new Random();

        while (uniqueNumbers.size() < NUMBERS_PER_CARD) {
            int randomNumber = random.nextInt(MAX_NUMBER_VALUE - MIN_NUMBER_VALUE + 1) + MIN_NUMBER_VALUE;
            uniqueNumbers.add(randomNumber);
        }

        Iterator<Integer> iterator = uniqueNumbers.iterator();

        for (int i = 0; i < CARD_SIZE; i++) {
            for (int j = 0; j < CARD_SIZE; j++) {
                int num = iterator.next(); // Obtém o próximo número único
                this.numbers[i][j] = num;
                this.linearNumbers.add(num);
            }
        }
    }

    public String getId() {
        return id;
    }

    public int[][] getNumbersGrid() {
        return numbers;
    }

    public List<Integer> getLinearNumbers() { //evitar modificaçoe externas
        return Collections.unmodifiableList(linearNumbers);
    }

    public String getNumbersAsCommaSeparatedString() {
        return this.linearNumbers.stream()
                .map(String::valueOf) // Converte Integer para String
                .collect(Collectors.joining(",")); // Junta com ","       
    }

    public boolean checkLine(List<Integer> drawnNumbersList) {
        if (drawnNumbersList == null || drawnNumbersList.size() < CARD_SIZE) {
            return false; // Impossível ter linha com menos de CARD_SIZE números sorteados
        }
        Set<Integer> drawnNumbersSet = new HashSet<>(drawnNumbersList); // Para pesquisa rápida (O(1) em média)

        // Verificar linhas horizontais
        for (int i = 0; i < CARD_SIZE; i++) {
            boolean lineComplete = true;
            for (int j = 0; j < CARD_SIZE; j++) {
                if (!drawnNumbersSet.contains(this.numbers[i][j])) {
                    lineComplete = false;
                    break; // Não é preciso continuar a verificar esta linha
                }
            }
            if (lineComplete) {
                return true; // Encontrou uma linha horizontal completa
            }
        }

        // Verificar linhas verticais
        for (int j = 0; j < CARD_SIZE; j++) {
            boolean lineComplete = true;
            for (int i = 0; i < CARD_SIZE; i++) {
                if (!drawnNumbersSet.contains(this.numbers[i][j])) {
                    lineComplete = false;
                    break; // Não é preciso continuar a verificar esta coluna
                }
            }
            if (lineComplete) {
                return true; // Encontrou uma linha vertical completa
            }
        }
        boolean diag1Complete = true;
        for (int i = 0; i < CARD_SIZE; i++) {
            if (!drawnNumbersSet.contains(this.numbers[i][i])) {
                diag1Complete = false;
                break;
            }
        }
        if (diag1Complete) {
            return true;
        }

        // Diagonal secundária (canto superior direito para inferior esquerdo)
        boolean diag2Complete = true;
        for (int i = 0; i < CARD_SIZE; i++) {
            if (!drawnNumbersSet.contains(this.numbers[i][CARD_SIZE - 1 - i])) {
                diag2Complete = false;
                break;
            }
        }
        if (diag2Complete) {
            return true;
        }

        return false; // Nenhuma linha encontrada
    }

    public boolean checkBingo(List<Integer> drawnNumbersList) {
        if (drawnNumbersList == null || drawnNumbersList.size() < NUMBERS_PER_CARD) {
            return false; // Não pode ter bingo com menos números sorteados do que os do cartão
        }

        Set<Integer> drawnNumbersSet = new HashSet<>(drawnNumbersList);

        for (int numOnCard : this.linearNumbers) {
            if (!drawnNumbersSet.contains(numOnCard)) {
                return false; // Encontrou um número do cartão que ainda não foi sorteado
            }
        }
        return true; // Todos os números do cartão foram sorteados
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Card ID: ").append(this.id).append("\n");
        for (int i = 0; i < CARD_SIZE; i++) {
            for (int j = 0; j < CARD_SIZE; j++) {
                sb.append(String.format("%02d ", this.numbers[i][j])); // %02d formata o número com 2 dígitos, preenchendo com zero à esquerda se necessário (ex: 07)
            }
            sb.append("\n"); // Nova linha após cada linha do cartão
        }
        return sb.toString();
    }
}