/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}"
  ],
  theme: {
    extend: {
      colors: {
        corporate: {
          bg: '#F8FAFC',
          card: '#FFFFFF',
          border: '#E2E8F0',
          heading: '#0F172A',
          body: '#334155',
          muted: '#64748B',
          primary: '#2563EB', // Enterprise Blue
          primaryHover: '#1D4ED8',
          accent: '#4F46E5', // Deep Indigo
          focusBorder: '#6366F1'
        }
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'Inter', 'sans-serif'],
        heading: ['Outfit', 'Inter', 'sans-serif']
      },
      boxShadow: {
        'corporate-sm': '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
        'corporate-md': '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.03)',
        'corporate-lg': '0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -4px rgba(0, 0, 0, 0.02)',
      }
    },
  },
  plugins: [],
}
