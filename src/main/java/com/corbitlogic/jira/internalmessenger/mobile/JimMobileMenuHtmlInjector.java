/*
 * Decompiled with CFR 0.152.
 */
package com.corbitlogic.jira.internalmessenger.mobile;

public final class JimMobileMenuHtmlInjector {
    static final String MENU_ITEM_MARKER = "jim-corbitchat-mobile";
    static final String SIDE_NAV_OTHER_MARKER = "id=\"side-nav-other\"";
    static final String FEEDBACK_MARKER = "<li><a id=\"feedback\"";
    static final String DESKTOP_MARKER = "<li><a id=\"switch-to-desktop\"";

    private JimMobileMenuHtmlInjector() {
    }

    public static boolean shouldInject(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        if (html.contains(MENU_ITEM_MARKER)) {
            return false;
        }
        if (!html.contains(SIDE_NAV_OTHER_MARKER)) {
            return false;
        }
        return html.contains("Assigned to Me") && html.contains("Give Feedback");
    }

    public static String inject(String html, String contextPath) {
        if (!JimMobileMenuHtmlInjector.shouldInject(html)) {
            return html;
        }
        String normalizedContext = contextPath == null ? "" : contextPath.trim();
        String chatHref = normalizedContext + "/plugins/servlet/jim/chat";
        String menuItem = "<li data-jim-mobile-menu-item=\"true\"><a id=\"jim-corbitchat-mobile\" href=\"" + chatHref + "\">CorbitChat</a></li>";
        int feedbackIndex = html.indexOf(FEEDBACK_MARKER);
        if (feedbackIndex >= 0) {
            return html.substring(0, feedbackIndex) + menuItem + html.substring(feedbackIndex);
        }
        int desktopIndex = html.indexOf(DESKTOP_MARKER);
        if (desktopIndex >= 0) {
            return html.substring(0, desktopIndex) + menuItem + html.substring(desktopIndex);
        }
        int sideNavIndex = html.indexOf(SIDE_NAV_OTHER_MARKER);
        if (sideNavIndex < 0) {
            return html;
        }
        int closeUlIndex = html.indexOf("</ul>", sideNavIndex);
        if (closeUlIndex < 0) {
            return html;
        }
        return html.substring(0, closeUlIndex) + menuItem + html.substring(closeUlIndex);
    }
}

