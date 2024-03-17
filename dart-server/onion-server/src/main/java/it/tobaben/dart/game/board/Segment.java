package it.tobaben.dart.game.board;

import lombok.Getter;

@Getter
public enum Segment {
    WALL_HIT(0, "WH"), BOARD_HIT(0, "BH"),
    SINGLE_1(1, "S1"), SINGLE_2(2, "S2"), SINGLE_3(3, "S3"), SINGLE_4(4, "S4"), SINGLE_5(5, "S5"),
    SINGLE_6(6, "S6"), SINGLE_7(7, "S7"), SINGLE_8(8, "S8"), SINGLE_9(9, "S9"), SINGLE_10(10, "S10"),
    SINGLE_11(11, "S11"), SINGLE_12(12, "S12"), SINGLE_13(13, "S13"), SINGLE_14(14, "S14"), SINGLE_15(15, "S15"),
    SINGLE_16(16, "S16"), SINGLE_17(17, "S17"), SINGLE_18(18, "S18"), SINGLE_19(19, "S19"), SINGLE_20(20, "S20"),
    DOUBLE_1(2, "D1"), DOUBLE_2(4, "D2"), DOUBLE_3(6, "D3"), DOUBLE_4(8, "D4"), DOUBLE_5(10, "D5"),
    DOUBLE_6(12, "D6"), DOUBLE_7(14, "D7"), DOUBLE_8(16, "D8"), DOUBLE_9(18, "D9"), DOUBLE_10(20, "D10"),
    DOUBLE_11(22, "D11"), DOUBLE_12(24, "D12"), DOUBLE_13(26, "D13"), DOUBLE_14(28, "D14"), DOUBLE_15(30, "D15"),
    DOUBLE_16(32, "D16"), DOUBLE_17(34, "D17"), DOUBLE_18(36, "D18"), DOUBLE_19(38, "D19"), DOUBLE_20(40, "D20"),
    TRIPLE_1(3, "T1"), TRIPLE_2(6, "T2"), TRIPLE_3(9, "T3"), TRIPLE_4(12, "T4"), TRIPLE_5(15, "T5"),
    TRIPLE_6(18, "T6"), TRIPLE_7(21, "T7"), TRIPLE_8(24, "T8"), TRIPLE_9(27, "T9"), TRIPLE_10(30, "T10"),
    TRIPLE_11(33, "T11"), TRIPLE_12(36, "T12"), TRIPLE_13(39, "T13"), TRIPLE_14(42, "T14"), TRIPLE_15(45, "T15"),
    TRIPLE_16(48, "T16"), TRIPLE_17(51, "T17"), TRIPLE_18(54, "T18"), TRIPLE_19(57, "T19"), TRIPLE_20(60, "T20"),
    BULL(25, "B"), BULLS_EYE(50, "BE");

    private final int score;
    private final String code;

    Segment(int score, String code) {
        this.score = score;
        this.code = code;
    }


}
