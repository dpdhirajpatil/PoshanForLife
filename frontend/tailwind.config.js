/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: ['./src/**/*.{html,ts}'],
  theme: {
    extend: {
      fontFamily: {
        // Generic names so swapping the real Gilroy/Alexander Lettering files
        // in later only means changing the @font-face src, not these keys.
        display: ['var(--font-display)', 'ui-sans-serif', 'sans-serif'],
        sans: ['var(--font-body)', 'ui-sans-serif', 'sans-serif'],
        accent: ['var(--font-accent)', 'cursive'],
      },
      colors: {
        // Semantic slots — components should reach for these, not the
        // brand-* families directly, so light/dark and future re-theming
        // stays a CSS-variable change only.
        background: 'var(--background)',
        foreground: 'var(--foreground)',
        border: 'var(--border)',
        input: 'var(--input)',
        ring: 'var(--ring)',
        card: {
          DEFAULT: 'var(--card)',
          foreground: 'var(--card-foreground)',
        },
        popover: {
          DEFAULT: 'var(--popover)',
          foreground: 'var(--popover-foreground)',
        },
        primary: {
          DEFAULT: 'var(--primary)',
          foreground: 'var(--primary-foreground)',
          // Built FROM the 5 curated green swatches, not a generated tonal
          // scale — 100/300/500/700/900 are the real brand swatches
          // verbatim; 50/200/400/600/800 fill Tailwind's numeric gaps for
          // existing call sites (avatars, active-nav states).
          50: '#f7f8ef',
          100: 'var(--brand-green-lightest)',
          200: '#e4e690',
          300: 'var(--brand-green-light)',
          400: '#cdd350',
          500: 'var(--brand-green)',
          600: '#b7bc2f',
          700: 'var(--brand-green-dark)',
          800: '#75762d',
          900: 'var(--brand-green-darkest)',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          foreground: 'var(--secondary-foreground)',
        },
        muted: {
          DEFAULT: 'var(--muted)',
          foreground: 'var(--muted-foreground)',
        },
        accent: {
          DEFAULT: 'var(--accent)',
          foreground: 'var(--accent-foreground)',
        },
        destructive: {
          DEFAULT: 'var(--destructive)',
          foreground: 'var(--destructive-foreground)',
        },
        sidebar: {
          DEFAULT: 'var(--sidebar)',
          foreground: 'var(--sidebar-foreground)',
          primary: 'var(--sidebar-primary)',
          'primary-foreground': 'var(--sidebar-primary-foreground)',
          accent: 'var(--sidebar-accent)',
          'accent-foreground': 'var(--sidebar-accent-foreground)',
          border: 'var(--sidebar-border)',
          ring: 'var(--sidebar-ring)',
        },
        chart: {
          1: 'var(--chart-1)',
          2: 'var(--chart-2)',
          3: 'var(--chart-3)',
          4: 'var(--chart-4)',
          5: 'var(--chart-5)',
        },
        // The full curated CI palette, exposed directly for anything that
        // needs a specific family/shade rather than a semantic slot (e.g.
        // TrapeziumBar, role badges, chart series).
        'brand-green': {
          darkest: 'var(--brand-green-darkest)',
          dark: 'var(--brand-green-dark)',
          DEFAULT: 'var(--brand-green)',
          light: 'var(--brand-green-light)',
          lightest: 'var(--brand-green-lightest)',
        },
        'brand-navy': {
          darkest: 'var(--brand-navy-darkest)',
          dark: 'var(--brand-navy-dark)',
          DEFAULT: 'var(--brand-navy)',
          light: 'var(--brand-navy-light)',
          lightest: 'var(--brand-navy-lightest)',
        },
        'brand-olive': {
          900: 'var(--brand-olive-900)',
          700: 'var(--brand-olive-700)',
          500: 'var(--brand-olive-500)',
          300: 'var(--brand-olive-300)',
          100: 'var(--brand-olive-100)',
        },
        'brand-berry': {
          900: 'var(--brand-berry-900)',
          700: 'var(--brand-berry-700)',
          500: 'var(--brand-berry-500)',
          300: 'var(--brand-berry-300)',
          100: 'var(--brand-berry-100)',
        },
        'brand-rust': {
          900: 'var(--brand-rust-900)',
          700: 'var(--brand-rust-700)',
          500: 'var(--brand-rust-500)',
          300: 'var(--brand-rust-300)',
          100: 'var(--brand-rust-100)',
        },
        'brand-gold': {
          900: 'var(--brand-gold-900)',
          700: 'var(--brand-gold-700)',
          500: 'var(--brand-gold-500)',
          300: 'var(--brand-gold-300)',
          100: 'var(--brand-gold-100)',
        },
        'brand-plum': {
          900: 'var(--brand-plum-900)',
          700: 'var(--brand-plum-700)',
          500: 'var(--brand-plum-500)',
          300: 'var(--brand-plum-300)',
          100: 'var(--brand-plum-100)',
        },
        'brand-indigo': {
          900: 'var(--brand-indigo-900)',
          700: 'var(--brand-indigo-700)',
          500: 'var(--brand-indigo-500)',
          300: 'var(--brand-indigo-300)',
          100: 'var(--brand-indigo-100)',
        },
      },
      borderRadius: {
        // Generously rounded per the CI guide's soft/clean/minimal language.
        sm: 'calc(var(--radius) - 4px)',
        md: 'calc(var(--radius) - 2px)',
        lg: 'var(--radius)',
        xl: 'calc(var(--radius) + 4px)',
      },
    },
  },
  plugins: [],
};
