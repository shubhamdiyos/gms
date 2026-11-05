import 'package:flutter/material.dart';

/// Color palette for the GMS mobile application.
///
/// Based on the Stitch design system. All colors are defined as hex values
/// for consistency with the design specifications.
class AppColors {
  AppColors._();

  // Primary colors
  static const Color primary = Color(0xFF2563EB);
  static const Color primaryDark = Color(0xFF1D4ED8);
  static const Color primaryLight = Color(0xFF60A5FA);

  // Secondary colors
  static const Color secondary = Color(0xFF10B981);
  static const Color secondaryDark = Color(0xFF059669);
  static const Color secondaryLight = Color(0xFF34D399);

  // Neutral colors
  static const Color white = Color(0xFFFFFFFF);
  static const Color gray50 = Color(0xFFF9FAFB);
  static const Color gray100 = Color(0xFFF3F4F6);
  static const Color gray200 = Color(0xFFE5E7EB);
  static const Color gray300 = Color(0xFFD1D5DB);
  static const Color gray400 = Color(0xFF9CA3AF);
  static const Color gray500 = Color(0xFF6B7280);
  static const Color gray600 = Color(0xFF4B5563);
  static const Color gray700 = Color(0xFF374151);
  static const Color gray800 = Color(0xFF1F2937);
  static const Color gray900 = Color(0xFF111827);
  static const Color black = Color(0xFF000000);

  // Semantic colors
  static const Color success = Color(0xFF10B981);
  static const Color warning = Color(0xFFF59E0B);
  static const Color error = Color(0xFFEF4444);
  static const Color info = Color(0xFF3B82F6);

  // Background colors
  static const Color background = white;
  static const Color surface = gray50;
  static const Color surfaceElevated = white;

  // Text colors
  static const Color textPrimary = gray900;
  static const Color textSecondary = gray600;
  static const Color textTertiary = gray500;
  static const Color textDisabled = gray400;
  static const Color textInverse = white;

  // Border colors
  static const Color border = gray200;
  static const Color borderDark = gray300;
  static const Color borderFocus = primary;

  // Role-specific colors
  static const Color roleAdmin = Color(0xFF7C3AED);
  static const Color roleTeacher = Color(0xFF2563EB);
  static const Color roleStudent = Color(0xFF10B981);
  static const Color roleParent = Color(0xFFF59E0B);
  static const Color roleAccountant = Color(0xFFEC4899);
}
