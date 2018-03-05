package org.softcake.yubari.documentation;

// tag::exampleDemo[]
/**
 * Documentation class.
 *
 * @author The softcake authors.
 */
public final class Documentation {

    private Documentation() {

        throw new IllegalStateException("No instances!");
    }

    public static void checkNotNull(final Object obj) {

        if (obj == null) {
            throw new IllegalArgumentException("Parameter must not be null!");
        }
    }

}
// end::exampleDemo[]
