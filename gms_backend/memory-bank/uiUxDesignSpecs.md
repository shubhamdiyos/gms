# GMS Frontend Development: UI/UX Design Specifications

## Design System Overview

### Color Palette
```scss
// Primary Colors
$primary-main: #1976d2;      // Blue - Trust and professionalism
$primary-light: #42a5f5;
$primary-dark: #1565c0;
$primary-contrast: #ffffff;

// Secondary Colors
$secondary-main: #388e3c;    // Green - Growth and education
$secondary-light: #66bb6a;
$secondary-dark: #2e7d32;
$secondary-contrast: #ffffff;

// Accent Colors
$accent-main: #f57c00;       // Orange - Highlights and calls-to-action
$accent-light: #ffad42;
$accent-dark: #ef6c00;
$accent-contrast: #ffffff;

// Neutral Colors
$neutral-light: #f5f5f5;     // Backgrounds
$neutral-main: #e0e0e0;
$neutral-dark: #9e9e9e;
$neutral-darker: #616161;
$neutral-darkest: #212121;   // Primary text

// Status Colors
$status-success: #4caf50;
$status-warning: #ff9800;
$status-error: #f44336;
$status-info: #2196f3;

// Semantic Colors
$text-primary: rgba(0, 0, 0, 0.87);
$text-secondary: rgba(0, 0, 0, 0.6);
$text-disabled: rgba(0, 0, 0, 0.38);
$divider: rgba(0, 0, 0, 0.12);
$background-paper: #ffffff;
$background-default: #f5f5f5;
```

### Typography
```scss
// Font Family
$font-family-primary: 'Roboto', 'Helvetica', 'Arial', sans-serif;

// Font Sizes
$font-size-xs: 12px;      // Helper text, captions
$font-size-sm: 14px;      // Body text, labels
$font-size-md: 16px;      // Default body text
$font-size-lg: 18px;      // Headings, subheadings
$font-size-xl: 20px;      // Section headings
$font-size-xxl: 24px;     // Page headings
$font-size-xxxl: 32px;    // Dashboard headings

// Font Weights
$font-weight-regular: 400;
$font-weight-medium: 500;
$font-weight-bold: 700;

// Line Heights
$line-height-dense: 1.2;
$line-height-normal: 1.5;
$line-height-loose: 1.7;
```

### Spacing System
```scss
// Base Unit: 8px grid
$spacing-unit: 8px;

$spacing-0: 0;
$spacing-1: $spacing-unit * 0.5;     // 4px
$spacing-2: $spacing-unit;            // 8px
$spacing-3: $spacing-unit * 1.5;      // 12px
$spacing-4: $spacing-unit * 2;        // 16px
$spacing-5: $spacing-unit * 3;        // 24px
$spacing-6: $spacing-unit * 4;        // 32px
$spacing-7: $spacing-unit * 5;        // 40px
$spacing-8: $spacing-unit * 6;        // 48px
$spacing-9: $spacing-unit * 8;        // 64px
$spacing-10: $spacing-unit * 10;      // 80px
$spacing-11: $spacing-unit * 12;      // 96px
$spacing-12: $spacing-unit * 16;      // 128px
```

### Breakpoints
```scss
// Mobile First Approach
$breakpoint-xs: 0;           // Extra small devices (portrait phones)
$breakpoint-sm: 600px;       // Small devices (landscape phones)
$breakpoint-md: 960px;       // Medium devices (tablets)
$breakpoint-lg: 1280px;      // Large devices (desktops)
$breakpoint-xl: 1920px;      // Extra large devices (large desktops)
```

### Shadows
```scss
$shadow-1: 0px 2px 1px -1px rgba(0,0,0,0.2), 0px 1px 1px 0px rgba(0,0,0,0.14), 0px 1px 3px 0px rgba(0,0,0,0.12);
$shadow-2: 0px 3px 1px -2px rgba(0,0,0,0.2), 0px 2px 2px 0px rgba(0,0,0,0.14), 0px 1px 5px 0px rgba(0,0,0,0.12);
$shadow-3: 0px 3px 3px -2px rgba(0,0,0,0.2), 0px 3px 4px 0px rgba(0,0,0,0.14), 0px 1px 8px 0px rgba(0,0,0,0.12);
$shadow-4: 0px 2px 4px -1px rgba(0,0,0,0.2), 0px 4px 5px 0px rgba(0,0,0,0.14), 0px 1px 10px 0px rgba(0,0,0,0.12);
$shadow-5: 0px 3px 5px -1px rgba(0,0,0,0.2), 0px 5px 8px 0px rgba(0,0,0,0.14), 0px 1px 14px 0px rgba(0,0,0,0.12);
```

## Component Design Specifications

### Buttons
```scss
// Base Button Styles
.button {
  font-family: $font-family-primary;
  font-weight: $font-weight-medium;
  font-size: $font-size-sm;
  line-height: 1.75;
  letter-spacing: 0.02857em;
  text-transform: uppercase;
  min-width: 64px;
  padding: 6px 16px;
  border-radius: 4px;
  border: 0;
  cursor: pointer;
  transition: background-color 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms,
              box-shadow 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms,
              border-color 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms,
              color 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  outline: 0;
  position: relative;
  box-sizing: border-box;
  user-select: none;
  vertical-align: middle;
  appearance: none;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

// Button Variants
.button-contained {
  box-shadow: $shadow-2;
  &:hover {
    box-shadow: $shadow-4;
  }
  &:active {
    box-shadow: $shadow-8;
  }
}

.button-outlined {
  border: 1px solid rgba(0, 0, 0, 0.23);
  &:hover {
    border: 1px solid rgba(0, 0, 0, 0.87);
    background-color: rgba(0, 0, 0, 0.04);
  }
}

.button-text {
  &:hover {
    background-color: rgba(0, 0, 0, 0.04);
  }
}

// Button Sizes
.button-small {
  padding: 4px 10px;
  font-size: $font-size-xs;
}

.button-medium {
  padding: 6px 16px;
  font-size: $font-size-sm;
}

.button-large {
  padding: 8px 22px;
  font-size: $font-size-md;
}

// Button Colors
.button-primary {
  color: $primary-contrast;
  background-color: $primary-main;
  &:hover {
    background-color: $primary-dark;
  }
}

.button-secondary {
  color: $secondary-contrast;
  background-color: $secondary-main;
  &:hover {
    background-color: $secondary-dark;
  }
}

.button-success {
  color: #ffffff;
  background-color: $status-success;
  &:hover {
    background-color: darken($status-success, 10%);
  }
}

.button-error {
  color: #ffffff;
  background-color: $status-error;
  &:hover {
    background-color: darken($status-error, 10%);
  }
}
```

### Cards
```scss
.card {
  border-radius: 8px;
  box-shadow: $shadow-1;
  background-color: $background-paper;
  overflow: hidden;
  transition: box-shadow 300ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  
  &:hover {
    box-shadow: $shadow-3;
  }
}

.card-header {
  padding: $spacing-4;
  border-bottom: 1px solid $divider;
  
  .card-title {
    margin: 0;
    font-weight: $font-weight-bold;
    font-size: $font-size-xl;
    color: $text-primary;
  }
  
  .card-subtitle {
    margin: $spacing-1 0 0 0;
    font-weight: $font-weight-regular;
    font-size: $font-size-sm;
    color: $text-secondary;
  }
}

.card-content {
  padding: $spacing-4;
  
  &:last-child {
    padding-bottom: $spacing-4;
  }
}

.card-actions {
  padding: $spacing-2 $spacing-4 $spacing-4;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  
  & > * + * {
    margin-left: $spacing-2;
  }
}
```

### Forms
```scss
.form-group {
  margin-bottom: $spacing-4;
}

.form-label {
  display: block;
  margin-bottom: $spacing-1;
  font-weight: $font-weight-medium;
  font-size: $font-size-sm;
  color: $text-primary;
}

.form-input {
  width: 100%;
  padding: $spacing-2;
  font-size: $font-size-md;
  line-height: 1.4375em;
  color: $text-primary;
  background-color: $background-paper;
  border: 1px solid $neutral-main;
  border-radius: 4px;
  box-sizing: border-box;
  transition: border-color 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms,
              box-shadow 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  
  &:focus {
    outline: 0;
    border-color: $primary-main;
    box-shadow: 0 0 0 2px rgba($primary-main, 0.2);
  }
  
  &::placeholder {
    color: $text-disabled;
    opacity: 1;
  }
  
  &:disabled {
    background-color: rgba(0, 0, 0, 0.04);
    color: $text-disabled;
    cursor: not-allowed;
  }
}

.form-select {
  @extend .form-input;
  appearance: none;
  background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  background-repeat: no-repeat;
  background-position: right 12px center;
  background-size: 16px;
  padding-right: 40px;
}

.form-textarea {
  @extend .form-input;
  min-height: 100px;
  resize: vertical;
}

.form-error {
  margin-top: $spacing-1;
  font-size: $font-size-xs;
  color: $status-error;
}

.form-helper {
  margin-top: $spacing-1;
  font-size: $font-size-xs;
  color: $text-secondary;
}
```

### Tables
```scss
.table-container {
  width: 100%;
  overflow-x: auto;
}

.table {
  width: 100%;
  border-collapse: collapse;
  background-color: $background-paper;
}

.table-head {
  background-color: rgba($primary-main, 0.08);
}

.table-header-cell {
  padding: $spacing-3;
  font-weight: $font-weight-bold;
  font-size: $font-size-sm;
  color: $text-primary;
  text-align: left;
  border-bottom: 1px solid $divider;
  
  &:first-child {
    border-top-left-radius: 4px;
  }
  
  &:last-child {
    border-top-right-radius: 4px;
  }
}

.table-cell {
  padding: $spacing-3;
  font-size: $font-size-sm;
  color: $text-primary;
  border-bottom: 1px solid $divider;
}

.table-row {
  &:hover {
    background-color: rgba(0, 0, 0, 0.04);
  }
  
  &:last-child {
    .table-cell {
      border-bottom: none;
    }
  }
}

.table-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: $spacing-2;
  border-top: 1px solid $divider;
}

.table-pagination-rows-per-page {
  margin-right: $spacing-4;
  font-size: $font-size-sm;
  color: $text-secondary;
}

.table-pagination-select {
  margin: 0 $spacing-2;
}

.table-pagination-actions {
  display: flex;
  align-items: center;
  
  & > * + * {
    margin-left: $spacing-2;
  }
}
```

### Navigation
```scss
// Sidebar Navigation
.sidebar {
  width: 240px;
  height: 100%;
  background-color: $background-paper;
  box-shadow: $shadow-1;
  display: flex;
  flex-direction: column;
  transition: transform 225ms cubic-bezier(0, 0, 0.2, 1) 0ms;
  
  @media (max-width: $breakpoint-md) {
    position: fixed;
    z-index: 1200;
    transform: translateX(-100%);
    
    &.open {
      transform: translateX(0);
    }
  }
}

.sidebar-header {
  padding: $spacing-3;
  border-bottom: 1px solid $divider;
  display: flex;
  align-items: center;
}

.sidebar-logo {
  height: 40px;
  margin-right: $spacing-2;
}

.sidebar-title {
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  color: $text-primary;
  margin: 0;
}

.sidebar-menu {
  flex: 1;
  padding: $spacing-2 0;
  overflow-y: auto;
}

.sidebar-menu-item {
  display: flex;
  align-items: center;
  padding: $spacing-2 $spacing-3;
  text-decoration: none;
  color: $text-secondary;
  transition: background-color 150ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  
  &:hover {
    background-color: rgba(0, 0, 0, 0.04);
    color: $text-primary;
  }
  
  &.active {
    background-color: rgba($primary-main, 0.08);
    color: $primary-main;
    font-weight: $font-weight-medium;
  }
}

.sidebar-menu-icon {
  margin-right: $spacing-3;
  color: inherit;
}

.sidebar-menu-text {
  flex: 1;
  font-size: $font-size-sm;
  font-weight: inherit;
  color: inherit;
}

.sidebar-footer {
  padding: $spacing-3;
  border-top: 1px solid $divider;
}

// Top Navigation
.topbar {
  height: 64px;
  background-color: $background-paper;
  box-shadow: $shadow-1;
  display: flex;
  align-items: center;
  padding: 0 $spacing-4;
  position: sticky;
  top: 0;
  z-index: 1100;
}

.topbar-menu-button {
  margin-right: $spacing-3;
  
  @media (min-width: $breakpoint-md) {
    display: none;
  }
}

.topbar-logo {
  height: 40px;
  margin-right: $spacing-3;
}

.topbar-title {
  font-size: $font-size-xl;
  font-weight: $font-weight-bold;
  color: $text-primary;
  margin: 0;
  flex: 1;
}

.topbar-spacer {
  flex: 1;
}

.topbar-actions {
  display: flex;
  align-items: center;
  
  & > * + * {
    margin-left: $spacing-3;
  }
}

.topbar-user-menu {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: $spacing-1 $spacing-2;
  border-radius: 4px;
  transition: background-color 150ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  
  &:hover {
    background-color: rgba(0, 0, 0, 0.04);
  }
}

.topbar-user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  margin-right: $spacing-2;
  background-color: $primary-main;
  color: $primary-contrast;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: $font-weight-bold;
  font-size: $font-size-sm;
}

.topbar-user-name {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $text-primary;
  margin-right: $spacing-1;
}

// Bottom Navigation (Mobile)
.bottom-navigation {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 56px;
  background-color: $background-paper;
  box-shadow: 0px -2px 4px -1px rgba(0,0,0,0.2), 0px -4px 5px 0px rgba(0,0,0,0.14), 0px -1px 10px 0px rgba(0,0,0,0.12);
  z-index: 1000;
  
  @media (min-width: $breakpoint-md) {
    display: none;
  }
}

.bottom-navigation-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 80px;
  padding: 6px 12px 8px;
  color: $text-secondary;
  text-decoration: none;
  transition: color 250ms cubic-bezier(0.4, 0, 0.2, 1) 0ms;
  
  &.active {
    color: $primary-main;
  }
}

.bottom-navigation-icon {
  margin-bottom: 4px;
  font-size: 24px;
}

.bottom-navigation-label {
  font-size: 12px;
  font-weight: 500;
  line-height: 1.5;
  letter-spacing: 0.00833em;
}
```

## Layout Specifications

### App Shell
```scss
.app-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.app-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.app-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: $spacing-4;
  
  @media (max-width: $breakpoint-sm) {
    padding: $spacing-3;
  }
}

.app-footer {
  padding: $spacing-3;
  background-color: $background-paper;
  border-top: 1px solid $divider;
  text-align: center;
  font-size: $font-size-xs;
  color: $text-secondary;
}
```

### Dashboard Layout
```scss
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: $spacing-4;
  margin-bottom: $spacing-4;
}

.dashboard-card {
  @extend .card;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.dashboard-card-header {
  @extend .card-header;
  padding: $spacing-3;
}

.dashboard-card-title {
  @extend .card-title;
  font-size: $font-size-lg;
}

.dashboard-card-content {
  @extend .card-content;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: $spacing-4;
}

.dashboard-card-value {
  font-size: $font-size-xxxl;
  font-weight: $font-weight-bold;
  color: $primary-main;
  margin-bottom: $spacing-2;
}

.dashboard-card-label {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.dashboard-card-footer {
  @extend .card-actions;
  padding: $spacing-2 $spacing-3 $spacing-3;
  justify-content: space-between;
}

.dashboard-chart-container {
  height: 300px;
  position: relative;
}
```

### Page Layout
```scss
.page-header {
  margin-bottom: $spacing-5;
  padding-bottom: $spacing-3;
  border-bottom: 1px solid $divider;
}

.page-title {
  margin: 0 0 $spacing-2 0;
  font-size: $font-size-xxl;
  font-weight: $font-weight-bold;
  color: $text-primary;
}

.page-subtitle {
  margin: 0 0 $spacing-3 0;
  font-size: $font-size-md;
  color: $text-secondary;
}

.page-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: $spacing-4;
  
  & > * + * {
    margin-left: $spacing-2;
  }
}

.page-content {
  background-color: $background-paper;
  border-radius: 8px;
  box-shadow: $shadow-1;
  padding: $spacing-4;
  
  @media (max-width: $breakpoint-sm) {
    padding: $spacing-3;
  }
}
```

## Responsive Design Guidelines

### Grid System
```scss
.container {
  width: 100%;
  padding-right: $spacing-3;
  padding-left: $spacing-3;
  margin-right: auto;
  margin-left: auto;
  
  @media (min-width: $breakpoint-sm) {
    max-width: 540px;
  }
  
  @media (min-width: $breakpoint-md) {
    max-width: 720px;
  }
  
  @media (min-width: $breakpoint-lg) {
    max-width: 960px;
  }
  
  @media (min-width: $breakpoint-xl) {
    max-width: 1140px;
  }
}

.grid {
  display: flex;
  flex-wrap: wrap;
  margin-right: -$spacing-2;
  margin-left: -$spacing-2;
}

.grid-item {
  padding-right: $spacing-2;
  padding-left: $spacing-2;
  margin-bottom: $spacing-4;
}

// Grid item widths for different breakpoints
@for $i from 1 through 12 {
  .grid-item-xs-#{$i} {
    flex: 0 0 percentage($i / 12);
    max-width: percentage($i / 12);
  }
}

@media (min-width: $breakpoint-sm) {
  @for $i from 1 through 12 {
    .grid-item-sm-#{$i} {
      flex: 0 0 percentage($i / 12);
      max-width: percentage($i / 12);
    }
  }
}

@media (min-width: $breakpoint-md) {
  @for $i from 1 through 12 {
    .grid-item-md-#{$i} {
      flex: 0 0 percentage($i / 12);
      max-width: percentage($i / 12);
    }
  }
}

@media (min-width: $breakpoint-lg) {
  @for $i from 1 through 12 {
    .grid-item-lg-#{$i} {
      flex: 0 0 percentage($i / 12);
      max-width: percentage($i / 12);
    }
  }
}

@media (min-width: $breakpoint-xl) {
  @for $i from 1 through 12 {
    .grid-item-xl-#{$i} {
      flex: 0 0 percentage($i / 12);
      max-width: percentage($i / 12);
    }
  }
}
```

### Media Query Mixins
```scss
@mixin respond-to($breakpoint) {
  @if $breakpoint == xs {
    @media (max-width: $breakpoint-sm - 1) {
      @content;
    }
  }
  
  @if $breakpoint == sm {
    @media (min-width: $breakpoint-sm) and (max-width: $breakpoint-md - 1) {
      @content;
    }
  }
  
  @if $breakpoint == md {
    @media (min-width: $breakpoint-md) and (max-width: $breakpoint-lg - 1) {
      @content;
    }
  }
  
  @if $breakpoint == lg {
    @media (min-width: $breakpoint-lg) and (max-width: $breakpoint-xl - 1) {
      @content;
    }
  }
  
  @if $breakpoint == xl {
    @media (min-width: $breakpoint-xl) {
      @content;
    }
  }
  
  @if $breakpoint == sm-and-up {
    @media (min-width: $breakpoint-sm) {
      @content;
    }
  }
  
  @if $breakpoint == md-and-up {
    @media (min-width: $breakpoint-md) {
      @content;
    }
  }
  
  @if $breakpoint == lg-and-up {
    @media (min-width: $breakpoint-lg) {
      @content;
    }
  }
  
  @if $breakpoint == xl-and-up {
    @media (min-width: $breakpoint-xl) {
      @content;
    }
  }
  
  @if $breakpoint == sm-and-down {
    @media (max-width: $breakpoint-md - 1) {
      @content;
    }
  }
  
  @if $breakpoint == md-and-down {
    @media (max-width: $breakpoint-lg - 1) {
      @content;
    }
  }
  
  @if $breakpoint == lg-and-down {
    @media (max-width: $breakpoint-xl - 1) {
      @content;
    }
  }
}
```

## Animation and Transition Guidelines

### Standard Transitions
```scss
$transition-standard: 300ms cubic-bezier(0.4, 0, 0.2, 1);
$transition-entering: 225ms cubic-bezier(0, 0, 0.2, 1);
$transition-leaving: 195ms cubic-bezier(0.4, 0, 1, 1);

.transition-standard {
  transition: all $transition-standard;
}

.transition-entering {
  transition: all $transition-entering;
}

.transition-leaving {
  transition: all $transition-leaving;
}
```

### Common Animations
```scss
// Fade In
@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.fade-in {
  animation: fadeIn 300ms $transition-standard;
}

// Slide In From Right
@keyframes slideInRight {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.slide-in-right {
  animation: slideInRight 300ms $transition-entering;
}

// Slide In From Left
@keyframes slideInLeft {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

.slide-in-left {
  animation: slideInLeft 300ms $transition-entering;
}

// Slide In From Top
@keyframes slideInTop {
  from {
    transform: translateY(-100%);
  }
  to {
    transform: translateY(0);
  }
}

.slide-in-top {
  animation: slideInTop 300ms $transition-entering;
}

// Slide In From Bottom
@keyframes slideInBottom {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.slide-in-bottom {
  animation: slideInBottom 300ms $transition-entering;
}

// Pulse
@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.05);
  }
  100% {
    transform: scale(1);
  }
}

.pulse {
  animation: pulse 2s infinite;
}
```

## Accessibility Guidelines

### Focus States
```scss
.focus-visible {
  outline: 2px solid $primary-main;
  outline-offset: 2px;
}

.focus-ring {
  box-shadow: 0 0 0 3px rgba($primary-main, 0.5);
}

.focus-invisible {
  outline: none;
}
```

### ARIA Attributes
```scss
// Visually hidden but accessible to screen readers
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}

// Skip link for keyboard navigation
.skip-link {
  position: absolute;
  top: -40px;
  left: 6px;
  background-color: $background-paper;
  color: $primary-main;
  padding: 8px;
  text-decoration: none;
  border-radius: 0 0 4px 4px;
  z-index: 10000;
  
  &:focus {
    top: 0;
  }
}
```

## Dark Mode Support

### Dark Theme Variables
```scss
// Dark Theme Colors
$dark-background-paper: #1e1e1e;
$dark-background-default: #121212;
$dark-text-primary: #ffffff;
$dark-text-secondary: rgba(255, 255, 255, 0.7);
$dark-text-disabled: rgba(255, 255, 255, 0.5);
$dark-divider: rgba(255, 255, 255, 0.12);

// Dark Theme Component Overrides
.theme-dark {
  background-color: $dark-background-default;
  color: $dark-text-primary;
  
  .card {
    background-color: $dark-background-paper;
    color: $dark-text-primary;
  }
  
  .form-input {
    background-color: $dark-background-paper;
    color: $dark-text-primary;
    border-color: rgba(255, 255, 255, 0.23);
    
    &:focus {
      border-color: $primary-main;
      box-shadow: 0 0 0 2px rgba($primary-main, 0.2);
    }
    
    &::placeholder {
      color: $dark-text-disabled;
    }
    
    &:disabled {
      background-color: rgba(255, 255, 255, 0.04);
      color: $dark-text-disabled;
    }
  }
  
  .table {
    background-color: $dark-background-paper;
    color: $dark-text-primary;
  }
  
  .table-header-cell {
    background-color: rgba($primary-main, 0.08);
    color: $dark-text-primary;
    border-bottom-color: $dark-divider;
  }
  
  .table-cell {
    color: $dark-text-primary;
    border-bottom-color: $dark-divider;
  }
  
  .sidebar {
    background-color: $dark-background-paper;
  }
  
  .topbar {
    background-color: $dark-background-paper;
  }
}
```

This UI/UX design specification provides comprehensive guidelines for creating a visually consistent, accessible, and responsive frontend for the GMS application. The design system includes color palettes, typography, spacing, component specifications, layout guidelines, and responsive design principles that will help frontend developers create a professional and user-friendly interface.