package it.tobaben.dart.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * This class would represent a player in the game, and would include information such as their name, and any other relevant details.
 */

@RequiredArgsConstructor
@Getter
public class Player {
    private final String name;

}
