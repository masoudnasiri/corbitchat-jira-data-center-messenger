package com.corbitlogic.jira.internalmessenger.mobile;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserPropertyManager;
import com.opensymphony.module.propertyset.PropertySet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-user board favorites for the mobile Board Gallery (Phase 7F).
 *
 * <p>Stored on the Jira user property set (no AO / schema change). The value is
 * a comma-separated list of numeric board ids, capped at {@link #MAX_FAVORITES}.
 * Callers must verify the viewer can see a board via
 * {@link JimMobileBoards#viewerCanSee} before adding an id — this helper does
 * not reveal or validate RapidView visibility.</p>
 */
public final class JimMobileBoardFavorites {

    static final String PROPERTY_KEY = "jim.board.favorites";
    public static final int MAX_FAVORITES = 40;

    private JimMobileBoardFavorites() {
    }

    public static Set<Long> idsFor(ApplicationUser user) {
        Set<Long> out = new LinkedHashSet<Long>();
        String raw = readRaw(user);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String part : raw.split(",")) {
            if (part == null) {
                continue;
            }
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(t);
                if (id > 0) {
                    out.add(id);
                }
            } catch (NumberFormatException ignore) {
                // skip junk
            }
            if (out.size() >= MAX_FAVORITES) {
                break;
            }
        }
        return out;
    }

    public static boolean contains(ApplicationUser user, long boardId) {
        return boardId > 0 && idsFor(user).contains(boardId);
    }

    /**
     * @return {@code false} when the add would exceed {@link #MAX_FAVORITES}
     *         or the property set cannot be written.
     */
    public static boolean setFavorite(ApplicationUser user, long boardId, boolean favorite) {
        if (user == null || boardId <= 0) {
            return false;
        }
        Set<Long> ids = idsFor(user);
        if (favorite) {
            if (ids.contains(boardId)) {
                return true;
            }
            if (ids.size() >= MAX_FAVORITES) {
                return false;
            }
            ids.add(boardId);
        } else {
            if (!ids.remove(boardId)) {
                return true;
            }
        }
        return writeRaw(user, join(ids));
    }

    public static boolean atLimit(ApplicationUser user, long boardId) {
        if (user == null) {
            return false;
        }
        Set<Long> ids = idsFor(user);
        return !ids.contains(boardId) && ids.size() >= MAX_FAVORITES;
    }

    public static void applyToCards(ApplicationUser user, Iterable<java.util.Map<String, Object>> cards) {
        Set<Long> favs = idsFor(user);
        if (cards == null) {
            return;
        }
        for (java.util.Map<String, Object> card : cards) {
            if (card == null) {
                continue;
            }
            card.put("favorite", isFav(card.get("id"), favs));
        }
    }

    private static boolean isFav(Object id, Set<Long> favs) {
        if (!(id instanceof Number)) {
            return false;
        }
        return favs.contains(((Number) id).longValue());
    }

    private static String join(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id.longValue());
        }
        return sb.toString();
    }

    private static String readRaw(ApplicationUser user) {
        try {
            PropertySet ps = propertySet(user);
            if (ps == null || !ps.exists(PROPERTY_KEY)) {
                return "";
            }
            try {
                String text = ps.getText(PROPERTY_KEY);
                if (text != null) {
                    return text;
                }
            } catch (Throwable ignore) {
                // may be stored as STRING
            }
            try {
                String s = ps.getString(PROPERTY_KEY);
                return s != null ? s : "";
            } catch (Throwable ignore) {
                return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean writeRaw(ApplicationUser user, String value) {
        try {
            PropertySet ps = propertySet(user);
            if (ps == null) {
                return false;
            }
            if (ps.exists(PROPERTY_KEY)) {
                try {
                    ps.remove(PROPERTY_KEY);
                } catch (Throwable ignore) {
                    // overwrite below
                }
            }
            if (value == null || value.isEmpty()) {
                return true;
            }
            ps.setText(PROPERTY_KEY, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static PropertySet propertySet(ApplicationUser user) {
        if (user == null) {
            return null;
        }
        UserPropertyManager mgr = null;
        try {
            mgr = ComponentAccessor.getUserPropertyManager();
        } catch (Throwable ignore) {
            // fall through
        }
        if (mgr == null) {
            try {
                mgr = ComponentAccessor.getComponent(UserPropertyManager.class);
            } catch (Throwable ignore) {
                return null;
            }
        }
        if (mgr == null) {
            return null;
        }
        return mgr.getPropertySet(user);
    }
}
