package io.github.angad7600123.cambio.currency

/**
 * Which of the two currencies the keypad is typing into.
 *
 * The converter is bidirectional: whichever side is active receives what you type
 * and the other side follows. Only the active figure is drawn in the foreground
 * colour, so it is always obvious where a digit will land.
 */
enum class ConversionSide {
    /** The upper figure in the app, the left one in the widget. */
    SOURCE,

    /** The lower figure in the app, the right one in the widget. */
    TARGET,
}
