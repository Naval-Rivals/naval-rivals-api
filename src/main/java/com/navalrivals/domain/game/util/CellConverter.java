package com.navalrivals.domain.game.util;

import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.infra.exception.exceptions.InvalidCellException;

/**
 * Converte entre o formato de célula string ("A1", "C4", "J10")
 * e o Position(row, col) numérico usado internamente.
 *
 * Convenção:
 *   Letra → row: A=0, B=1, C=2, ..., J=9
 *   Número → col: 1=0, 2=1, 3=2, ..., 10=9
 *
 * Exemplos:
 *   "A1"  → Position(0, 0)
 *   "C4"  → Position(2, 3)
 *   "J10" → Position(9, 9)
 */
public class CellConverter {

    private CellConverter(){}

    public static Position toPosition(String cell){
        if (cell == null || cell.length() < 2 || cell.length() > 3){
            throw new InvalidCellException("Célula inválida: " + cell);
        }

        char letter = Character.toUpperCase(cell.charAt(0));
        if (letter < 'A' || letter > 'J'){
            throw new InvalidCellException("Linha inválida: " + letter);
        }

        int row = letter - 'A';

        int col;
        try {
            col = Integer.parseInt(cell.substring(1)) - 1;
        }catch (NumberFormatException e){
            throw new InvalidCellException("Coluna inválida: " + cell.substring(1));
        }

        if(col < 0 || col > 9){
            throw new InvalidCellException("Coluna fora do range: " + (col + 1));
        }

        return new Position(row, col);
    }

    public static String toCell(Position position){
        char letter = (char) ('A' + position.getRow());
        int number = position.getCol() + 1;
        return "" + letter + number;
    }
}
