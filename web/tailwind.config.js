/**
 * The university's colours are CSS variables that shared/brand sets on the root once it knows the
 * university, so `bg-brand` is red at UPC and blue at USIL with the same class. Everything else
 * (neutrals, type, spacing) is the same for every university: the tokens of the design below.
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
        // The neutrals of the design (Figma variables of the same name).
        ink: {
          DEFAULT: '#1D2033',
          2: '#5A5F73',
          // Only for what is not text (dividers, the dots of a timeline): it reads 3:1 on white.
          3: '#9095A6',
        },
        line: '#E6E7EE',
        // Credits are always yellow, whatever the university: the coin and its chip.
        credit: {
          DEFAULT: '#FFC800',
          soft: '#FFF6D6',
          text: '#7A5E00',
        },
        // The informative tone (badges, the date of a tutoring, one's own avatar).
        info: {
          soft: '#E5F6FC',
          text: '#00749F',
        },
      },
      fontFamily: {
        // Titles (PageHeader, Card, Dialog, EmptyState); both are served from public/fonts.
        display: ['Outfit', 'system-ui', 'sans-serif'],
        sans: ['DM Sans', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
