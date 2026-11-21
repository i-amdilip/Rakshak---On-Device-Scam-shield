---
name: KAVACH Guardian
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#45464d'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#565e74'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#131b2e'
  on-primary-container: '#7c839b'
  inverse-primary: '#bec6e0'
  secondary: '#006a61'
  on-secondary: '#ffffff'
  secondary-container: '#86f2e4'
  on-secondary-container: '#006f66'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#002113'
  on-tertiary-container: '#009668'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#89f5e7'
  secondary-fixed-dim: '#6bd8cb'
  on-secondary-fixed: '#00201d'
  on-secondary-fixed-variant: '#005049'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  display:
    fontFamily: Inter
    fontSize: 34px
    fontWeight: '700'
    lineHeight: 42px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 26px
    fontWeight: '700'
    lineHeight: 34px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 22px
    fontWeight: '700'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 26px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  title-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 22px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.03em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-sm: 0.75rem
  gutter-lg: 1.5rem
  margin: 1rem
  margin-tablet: 1.5rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 3rem
---

## Brand & Style

This design system establishes a protective, composed, and instantly legible security environment tailored for Android. The design language marries modern technological precision with protective calm—eschewing panic-inducing cybersecurity tropes in favor of dignified reassurance, sovereign control, and absolute clarity.

The visual direction draws from high-trust modern product design:
- **Calm Authority:** Crisp white surfaces, deep grounding navy tones, and generous structural breathing room eliminate sensory overload during security events.
- **Immediate Legibility:** High-contrast typographic scale paired with unambiguous alert badges ensures critical diagnostic states are understood in an instant by users across all age demographics.
- **Physical Substance:** Subtle border strokes (1px) combined with diffused, low-opacity ambient shadows provide tactile confidence without clutter.
- **Tactile Reassurance:** Protective status cards and pill indicators communicate safety and scanning progression cleanly, establishing a steady, dependable presence.

## Colors

The color system delivers an authoritative hierarchy engineered for immediate state comprehension:

- **Primary (`#0F172A` / `#1E293B`):** Deep Trust Navy acts as the bedrock for navigation structures, heavy brand surfaces, high-contrast headings, and grounding hero elements.
- **Secondary (`#0D9488` / `#14B8A6`):** Vibrant Shield Teal drives active security states, active scanners, verified safe locks, and focused interactive highlights.
- **Neutral Canvas (`#F8FAFC`):** Soft Slate provides an easy-on-the-eyes app backdrop that reduces eye strain while allowing pure white surface cards (`#FFFFFF`) to visually elevate.
- **Status & Alert Hierarchy:**
  - **Safe Mint Green (`#10B981`):** Applied exclusively to confirmed secure states, verified communications, and positive scan results.
  - **Warning Amber (`#F59E0B`):** Denotes suspicious links, unknown callers, unverified permissions, and caution notices.
  - **High-Risk Alert Crimson (`#EF4444`):** Reserved strictly for critical scam threats, active fraudulent attempts, phishing blocks, and immediate intervention actions.

## Typography

Inter serves as the singular typographic voice across headlines, body copy, and status labels. Its tall x-height, neutral geometric structure, and open apertures maintain optimal legibility on mobile viewports under variable lighting.

- **Status & Numerical Metrics:** Use `display` or `headline-lg` at bold weights for scan statistics, numerical risk scores, and primary device shields.
- **Headings:** Set headings with slight negative letter tracking to impart modern authority without sacrificing scannability.
- **Body Content:** Kept at generous relative line heights (1.4x to 1.5x) to facilitate parsing of technical scam warnings and multi-step resolution advice.
- **Labels & Badges:** Utilize `label-md` and `label-sm` with slight positive tracking to ensure uppercase or title-cased pills remain distinct from running narrative text.

## Layout & Spacing

The layout is built on a 4px/8px incremental rhythm structured around mobile-first utility:

- **Mobile Canvas:** Single-column fluid layout with a base outer margin of `margin` (16px) and column gutter of `gutter` (16px). Key interactive touch targets conform to a minimum height of 48px.
- **Tablet / Large Handhelds:** Two-column grid with outer margins increased to `margin-tablet` (24px) for multi-pane threat inspection logs.
- **Component Padding Scale:**
  - `space-xs` (4px): Tight badge insets, icon-to-label gaps.
  - `space-sm` (8px): List item content spacing, button internal gaps.
  - `space-md` (16px): Standard card internal padding, screen element separation.
  - `space-lg` (24px): Hero card padding, major section delineation.
  - `space-xl` (32px) to `space-2xl` (48px): Primary scan shield visual anchoring and hero transitions.

## Elevation & Depth

Visual hierarchy leverages a hybrid model of soft structural borders and low-opacity ambient shadows, avoiding harsh skeuomorphism while preventing flat, ambiguous touch zones:

- **Surface Layers:**
  - Base canvas: `#F8FAFC` (Soft Slate).
  - Surface Tier 1 (Cards, Sheets): `#FFFFFF` (Pure White) with a 1px border of `#E2E8F0` and subtle ambient shadow (`rgba(15, 23, 42, 0.04)` blur 12px, y: 2px).
  - Surface Tier 2 (Floating Action Panels, Modals): `#FFFFFF` with a 1px border of `#CBD5E1` and ambient shadow (`rgba(15, 23, 42, 0.08)` blur 20px, y: 6px).
- **Security Alert Tiers:**
  - Alert banners drop heavy drop shadows in favor of a 1px tinted border and a 10% alpha fill of the relevant diagnostic hue (e.g., Safe Mint at 8% alpha background with a 20% alpha border).
- **Navigation Elevation:**
  - Bottom App Bar is grounded with `#FFFFFF`, elevated via a razor 1px top border of `#E2E8F0` and an upward ambient blur (`rgba(15, 23, 42, 0.03)` blur 16px, y: -4px).

## Shapes

The design system adopts a balanced roundedness profile (`roundedness: 2`) combining rounded corners for structural cards and full circular pills for status badges and primary actions:

- **Base Cards & Surfaces:** 8px (`rounded-md`) to 16px (`rounded-lg`) corner radius, projecting an engineered, deliberate structural feel.
- **Badges, Tags, and Primary FABs:** Full pill-shaped geometry (`rounded-full`), softening the UI and instantly signaling interactive or diagnostic status.
- **Shield Indicators & Visual Rings:** Perfectly circular diagnostic perimeters (`border-radius: 9999px`) to emphasize 360-degree real-time protection.
- **Input Fields & Search Bars:** 12px (`rounded-md` / `rounded-lg`) corner radius for comfortable touch ergonomics.

## Components

### Buttons
- **Primary Action (Scan / Protect):** Filled with Deep Trust Navy (`#0F172A`) or Vibrant Shield Teal (`#0D9488`), full pill radius (`rounded-full`), 48px height, `label-lg` white text. Pressed state transitions to an intensified dark shade with an inner 1px translucent ring.
- **Secondary Action:** Ghost or soft-tinted surface, `#F1F5F9` background, `#0F172A` text, 1px border of `#E2E8F0`.
- **Destructive / Urgent Block:** High-Risk Alert Red (`#EF4444`) filled button with white text, used for immediate threat isolation.

### Badges & Status Pills
- Pill-shaped (`rounded-full`), horizontal padding of 12px, vertical padding of 4px.
- **Safe Badge:** `#ECFDF5` background, `#10B981` text and dot icon, 1px `#A7F3D0` border.
- **Warning Badge:** `#FFFBEB` background, `#F59E0B` text and dot icon, 1px `#FDE68A` border.
- **High-Risk Badge:** `#FEF2F2` background, `#EF4444` text and exclamation icon, 1px `#FECACA` border.

### Cards & Threat Logs
- Pure White (`#FFFFFF`) background, 16px radius, 16px padding.
- Enclosed with a 1px border of `#E2E8F0`.
- Threat cards feature a 4px left accent border color-coded to the severity level (Mint, Amber, or Crimson) to enable vertical scanning.

### Input Fields (Scam Link & Number Verification)
- 48px height, 12px border radius, `#FFFFFF` background with a 1.5px `#E2E8F0` border.
- Focus state shifts border to Shield Teal (`#0D9488`) with a subtle 3px outer glow (`rgba(13, 148, 136, 0.12)`).
- Leading and trailing action icons (paste, clear, scan) sized at 20px in `#64748B`.

### Checkboxes & Toggle Switches
- Native-aligned Android toggle switches: Track fills with Vibrant Shield Teal (`#0D9488`) when active, `#CBD5E1` when inactive. Thumb remains Pure White (`#FFFFFF`).
- Checkboxes: 20px rounded squares (4px radius) filled with Navy (`#0F172A`) on selection with a crisp white checkmark.

### App Bar & Navigation
- **Android Top App Bar:** Minimalist 56px height, `#FFFFFF` surface with deep navy title (`title-md` or `headline-sm`), contextual shield status icon, and crisp icon buttons (settings, history).
- **Bottom Navigation Bar:** Fixed bottom container, 64px height, `#FFFFFF` background with a top 1px border. Navigation items include a 24px icon and `label-sm` text. Active item is marked with a Teal badge pill container and Navy text.