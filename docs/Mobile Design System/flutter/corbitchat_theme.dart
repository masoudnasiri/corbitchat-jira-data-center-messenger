import 'package:flutter/material.dart';

/// CorbitChat Design System — Flutter Theme
///
/// Usage:
///   MaterialApp(
///     theme: CorbitChatTheme.light(locale: 'fa-IR'),
///     darkTheme: CorbitChatTheme.dark(locale: 'fa-IR'),
///     themeMode: ThemeMode.system,
///   )
class CorbitChatTheme {
  CorbitChatTheme._();

  // Brand colors (light)
  static const Color primary = Color(0xFF0D6E6E);
  static const Color primaryHover = Color(0xFF0A5858);
  static const Color primaryMuted = Color(0xFFE6F4F4);
  static const Color secondary = Color(0xFF3D4F7C);
  static const Color secondaryMuted = Color(0xFFEEF1F7);
  static const Color accent = Color(0xFFE07A2F);
  static const Color workflow = Color(0xFF0052CC);

  // Surfaces (light)
  static const Color background = Color(0xFFF5F6F8);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color surfaceSubtle = Color(0xFFECEEF2);
  static const Color border = Color(0xFFD8DCE4);
  static const Color divider = Color(0xFFE8EBF0);

  // Text (light)
  static const Color textPrimary = Color(0xFF1A2332);
  static const Color textSecondary = Color(0xFF55627A);
  static const Color textTertiary = Color(0xFF6E7A8C);
  static const Color textDisabled = Color(0xFFA9B1BD);

  // Dark palette
  static const Color darkPrimary = Color(0xFF2CB5B5);
  static const Color darkPrimaryMuted = Color(0xFF12312F);
  static const Color darkSecondary = Color(0xFF97A6CC);
  static const Color darkAccent = Color(0xFFF0965A);
  static const Color darkWorkflow = Color(0xFF4C9AFF);
  static const Color darkBackground = Color(0xFF0E1116);
  static const Color darkSurface = Color(0xFF171B22);
  static const Color darkSurfaceSubtle = Color(0xFF1F242D);
  static const Color darkBorder = Color(0xFF2A303A);
  static const Color darkDivider = Color(0xFF232833);
  static const Color darkTextPrimary = Color(0xFFE7EBF0);
  static const Color darkTextSecondary = Color(0xFFA6B0BD);
  static const Color darkTextTertiary = Color(0xFF7E8A99);
  static const Color darkError = Color(0xFFF0736A);
  static const Color darkOnPrimary = Color(0xFF04211F);

  // Semantic
  static const Color success = Color(0xFF1F8A4C);
  static const Color successMuted = Color(0xFFE8F6EE);
  static const Color warning = Color(0xFFC47A00);
  static const Color warningMuted = Color(0xFFFFF4E0);
  static const Color error = Color(0xFFC62828);
  static const Color errorMuted = Color(0xFFFDECEC);
  static const Color info = Color(0xFF1565C0);
  static const Color infoMuted = Color(0xFFE8F0FA);

  // Status
  static const Color statusTodo = Color(0xFF6B7280);
  static const Color statusInProgress = Color(0xFF1565C0);
  static const Color statusDone = Color(0xFF1F8A4C);
  static const Color statusBlocked = Color(0xFFC62828);

  // Chat
  static const Color chatOutgoing = Color(0xFF0D6E6E);
  static const Color chatIncoming = Color(0xFFFFFFFF);
  static const Color chatUnread = Color(0xFFE07A2F);
  static const Color presenceOnline = Color(0xFF1F8A4C);

  // Spacing
  static const double touchMin = 44.0;
  static const double screenPadding = 16.0;
  static const double bottomNavHeight = 56.0;
  static const double topBarHeight = 56.0;

  // Radius
  static const double radiusSm = 6.0;
  static const double radiusMd = 10.0;
  static const double radiusLg = 14.0;
  static const double radiusBubble = 16.0;

  /// Light theme. `locale` selects the font family (fa → Vazirmatn, en → Inter).
  static ThemeData light({required String locale}) =>
      _build(locale: locale, brightness: Brightness.light);

  /// Dark theme — mirrors the CSS `[data-theme="dark"]` tokens.
  static ThemeData dark({required String locale}) =>
      _build(locale: locale, brightness: Brightness.dark);

  static ThemeData _build({
    required String locale,
    required Brightness brightness,
  }) {
    final isFa = locale.startsWith('fa');
    final fontFamily = isFa ? 'Vazirmatn' : 'Inter';
    final isDark = brightness == Brightness.dark;

    final cPrimary = isDark ? darkPrimary : primary;
    final cPrimaryMuted = isDark ? darkPrimaryMuted : primaryMuted;
    final cSecondary = isDark ? darkSecondary : secondary;
    final cBackground = isDark ? darkBackground : background;
    final cSurface = isDark ? darkSurface : surface;
    final cBorder = isDark ? darkBorder : border;
    final cDivider = isDark ? darkDivider : divider;
    final cText = isDark ? darkTextPrimary : textPrimary;
    final cTextSecondary = isDark ? darkTextSecondary : textSecondary;
    final cTextTertiary = isDark ? darkTextTertiary : textTertiary;
    final cError = isDark ? darkError : error;
    final cOnPrimary = isDark ? darkOnPrimary : Colors.white;
    final cOnDark = isDark ? darkBackground : Colors.white;

    final colorScheme = ColorScheme(
      brightness: brightness,
      primary: cPrimary,
      onPrimary: cOnPrimary,
      secondary: cSecondary,
      onSecondary: cOnDark,
      surface: cSurface,
      onSurface: cText,
      error: cError,
      onError: cOnDark,
      outline: cBorder,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: cBackground,
      fontFamily: fontFamily,
      dividerColor: cDivider,
      appBarTheme: AppBarTheme(
        elevation: 0,
        scrolledUnderElevation: 1,
        backgroundColor: cSurface,
        foregroundColor: cText,
        centerTitle: false,
        titleTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 17,
          fontWeight: FontWeight.w600,
          color: cText,
        ),
        toolbarHeight: topBarHeight,
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: cSurface,
        selectedItemColor: cPrimary,
        unselectedItemColor: cTextTertiary,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
        selectedLabelStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 11,
          fontWeight: FontWeight.w500,
        ),
        unselectedLabelStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 11,
          fontWeight: FontWeight.w500,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: cPrimary,
          foregroundColor: cOnPrimary,
          disabledBackgroundColor: cPrimary.withValues(alpha: 0.5),
          minimumSize: const Size(0, touchMin),
          padding: const EdgeInsets.symmetric(horizontal: 20),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusMd),
          ),
          textStyle: TextStyle(
            fontFamily: fontFamily,
            fontSize: 15,
            fontWeight: FontWeight.w500,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: cPrimary,
          minimumSize: const Size(0, touchMin),
          side: BorderSide(color: cBorder),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusMd),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: cSurface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusMd),
          borderSide: BorderSide(color: cBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusMd),
          borderSide: BorderSide(color: cBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusMd),
          borderSide: BorderSide(color: cPrimary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusMd),
          borderSide: BorderSide(color: cError),
        ),
        hintStyle: TextStyle(
          fontFamily: fontFamily,
          color: cTextTertiary,
          fontSize: 15,
        ),
      ),
      cardTheme: CardTheme(
        color: cSurface,
        elevation: 1,
        shadowColor: Colors.black.withValues(alpha: isDark ? 0.4 : 0.08),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusLg),
          side: BorderSide(color: cDivider),
        ),
        margin: EdgeInsets.zero,
      ),
      chipTheme: ChipThemeData(
        backgroundColor: cPrimaryMuted,
        labelStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 11,
          fontWeight: FontWeight.w500,
          color: cPrimary,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusSm),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: cText,
        contentTextStyle: TextStyle(
          fontFamily: fontFamily,
          fontSize: 13,
          color: cBackground,
        ),
        behavior: SnackBarBehavior.floating,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusMd),
        ),
      ),
      textTheme: _textTheme(fontFamily, cText, cTextSecondary),
    );
  }

  static TextTheme _textTheme(
    String fontFamily,
    Color textColor,
    Color secondaryColor,
  ) {
    return TextTheme(
      displaySmall: TextStyle(
        fontFamily: fontFamily,
        fontSize: 24,
        fontWeight: FontWeight.w700,
        height: 32 / 24,
        color: textColor,
      ),
      titleLarge: TextStyle(
        fontFamily: fontFamily,
        fontSize: 17,
        fontWeight: FontWeight.w600,
        height: 24 / 17,
        color: textColor,
      ),
      titleMedium: TextStyle(
        fontFamily: fontFamily,
        fontSize: 15,
        fontWeight: FontWeight.w600,
        height: 22 / 15,
        color: textColor,
      ),
      bodyLarge: TextStyle(
        fontFamily: fontFamily,
        fontSize: 15,
        fontWeight: FontWeight.w400,
        height: 22 / 15,
        color: textColor,
      ),
      bodyMedium: TextStyle(
        fontFamily: fontFamily,
        fontSize: 13,
        fontWeight: FontWeight.w400,
        height: 18 / 13,
        color: secondaryColor,
      ),
      labelSmall: TextStyle(
        fontFamily: fontFamily,
        fontSize: 11,
        fontWeight: FontWeight.w500,
        height: 16 / 11,
        color: secondaryColor,
      ),
    );
  }

  /// Status badge colors mapped from server field-display rules.
  static ({Color foreground, Color background}) statusColors(String category) {
    switch (category.toLowerCase()) {
      case 'done':
        return (foreground: statusDone, background: successMuted);
      case 'in progress':
      case 'inprogress':
        return (foreground: statusInProgress, background: infoMuted);
      case 'blocked':
        return (foreground: statusBlocked, background: errorMuted);
      default:
        return (foreground: statusTodo, background: surfaceSubtle);
    }
  }

  /// Priority badge colors.
  static ({Color foreground, Color background}) priorityColors(String priority) {
    switch (priority.toLowerCase()) {
      case 'highest':
        return (foreground: const Color(0xFFB71C1C), background: errorMuted);
      case 'high':
        return (foreground: const Color(0xFFE65100), background: const Color(0xFFFFF3E0));
      case 'medium':
        return (foreground: const Color(0xFFF9A825), background: const Color(0xFFFFFDE7));
      case 'low':
        return (foreground: const Color(0xFF546E7A), background: const Color(0xFFECEFF1));
      default:
        return (foreground: textTertiary, background: surfaceSubtle);
    }
  }

  /// نوع تسک (طبق دستورالعمل) — customfield_10903 pill colors.
  static ({Color foreground, Color background, String cssClass}) taskTypePill(String value) {
    final normalized = value.trim().toLowerCase();
    const map = {
      'critical': (fg: Color(0xFFDE350B), bg: Color(0xFFFFEBE6), cls: 'critical'),
      'بحرانی': (fg: Color(0xFFDE350B), bg: Color(0xFFFFEBE6), cls: 'critical'),
      'urgent': (fg: Color(0xFFFF8B00), bg: Color(0xFFFFF0B3), cls: 'urgent'),
      'فوری': (fg: Color(0xFFFF8B00), bg: Color(0xFFFFF0B3), cls: 'urgent'),
      'committed': (fg: Color(0xFFFFAB00), bg: Color(0xFFFFF7D6), cls: 'committed'),
      'تعهدی': (fg: Color(0xFFFFAB00), bg: Color(0xFFFFF7D6), cls: 'committed'),
      'current': (fg: Color(0xFF0052CC), bg: Color(0xFFDEEBFF), cls: 'current'),
      'جاری': (fg: Color(0xFF0052CC), bg: Color(0xFFDEEBFF), cls: 'current'),
      'project': (fg: Color(0xFF6554C0), bg: Color(0xFFEAE6FF), cls: 'project'),
      'project-based': (fg: Color(0xFF6554C0), bg: Color(0xFFEAE6FF), cls: 'project'),
      'پروژه ای': (fg: Color(0xFF6554C0), bg: Color(0xFFEAE6FF), cls: 'project'),
      'پروژه‌ای': (fg: Color(0xFF6554C0), bg: Color(0xFFEAE6FF), cls: 'project'),
      'development': (fg: Color(0xFF00875A), bg: Color(0xFFE3FCEF), cls: 'development'),
      'توسعه ای': (fg: Color(0xFF00875A), bg: Color(0xFFE3FCEF), cls: 'development'),
      'توسعه‌ای': (fg: Color(0xFF00875A), bg: Color(0xFFE3FCEF), cls: 'development'),
      'strategic': (fg: Color(0xFF172B4D), bg: Color(0xFFDFE1E6), cls: 'strategic'),
      'راهبردی': (fg: Color(0xFF172B4D), bg: Color(0xFFDFE1E6), cls: 'strategic'),
    };
    final entry = map[normalized];
    if (entry != null) {
      return (foreground: entry.fg, background: entry.bg, cssClass: entry.cls);
    }
    return (foreground: textSecondary, background: surfaceSubtle, cssClass: 'unknown');
  }

  /// Trello-style board colors.
  static const Color boardColumnBg = Color(0xFFEBECF0);
  static const Color boardColumnHeader = Color(0xFF5E6C84);
  static const Color boardCardBg = Color(0xFFFFFFFF);
}
