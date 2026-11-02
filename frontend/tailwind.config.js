/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef2ff',
          100: '#e0e7ff',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
        },
        ecom: {
          bg: '#111827',       // Main background: dark navy/charcoal
          surface: '#0F172A',  // Content surfaces
          header: '#101A2D',   // Sidebar / header
          card: '#172338',     // Cards
          panel: '#1C2A40',    // Panels / accent surfaces
          input: '#0B1425',    // Form inputs
          border: '#29384F',   // Border lines
          text: '#E8EEF7',     // Main text
          muted: '#94A3B8',    // Muted / secondary text
        }
      }
    },
  },
  plugins: [],
}
