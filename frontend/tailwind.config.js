/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#faf5ff',
          100: '#f3e8ff',
          200: '#e9d5ff',
          300: '#d8b4fe',
          400: '#c084fc',
          500: '#a855f7',
          600: '#9333ea',
          700: '#7e22ce',
          800: '#6b21a8',
          900: '#581c87',
          950: '#3e1f47',
        },
        neutral: {
          50: '#fafafa',
          100: '#f5f5f5',
          200: '#e5e5e5',
          300: '#d4d4d8',
          400: '#a3a3a3',
          500: '#737373',
          600: '#575757',
          700: '#3f3f3f',
          800: '#2b2b2b',
          900: '#1a1a1a',
          950: '#0a0a0a',
        },
      },
      backgroundColor: {
        page: '#fafafa',
        surface: '#ffffff',
        'surface-subtle': '#f5f5f5',
        'surface-hover': '#f0f0f0',
      },
      textColor: {
        body: '#1a1a1a',
        'body-muted': '#575757',
        'body-subtle': '#737373',
      },
      borderColor: {
        DEFAULT: '#e5e5e5',
        'subtle': '#f5f5f5',
      },
      boxShadow: {
        'sm': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
        'md': '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
        'lg': '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
        'xl': '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
      },
      fontFamily: {
        sans: ['-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
        mono: ['Fira Code', 'Monaco', 'Courier New', 'monospace'],
      },
      spacing: {
        '18': '4.5rem',
        '22': '5.5rem',
      },
    },
  },
  plugins: [],
}
