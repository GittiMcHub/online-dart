package it.tobaben.dart;

import it.tobaben.dart.game.board.Segment;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List listTest = new ArrayList<Segment>();
        listTest.add(Segment.DOUBLE_2);
        listTest.add(Segment.DOUBLE_2);

        System.out.println(listTest);

    }
}