package crawler.game;

import crawler.core.MapObject;

/**
 * Something the player has to read before carrying on: an event's message, a chest's
 * contents, a character's words.
 *
 * @param source the object it came from, or null
 */
public record GameMessage(String title, String text, MapObject source) {
}
