/**
 * The university's colours are CSS variables that shared/brand sets on the root once it knows the
 * university, so `bg-brand` is red at UPC and blue at USIL with the same class. Everything else
 * (neutrals, type, spacing) is Tailwind's and never changes between universities.
 *
 * @type {import('tailwindcss').Config}
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: 'var(--brand-primary)',
          primary: 'var(--brand-primary)',
          // The colour of text and icons drawn on the primary.
          secondary: 'var(--brand-secondary)',
          soft: 'var(--brand-primary-soft)',
          pressed: 'var(--brand-primary-pressed)',
          // The primary where it is text on grey or on the soft tint: darkened until it reads.
          text: 'var(--brand-text)',
        },
        // The grey of the page behind the cards.
        surface: '#F6F6F9',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
