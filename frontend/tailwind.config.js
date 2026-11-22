/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/**/*.{html,ts}',
  ],
  theme: {
    extend: {
      colors: {
        finpulse: {
          navy: '#0F172A',
          slate: '#1E293B',
          emerald: '#10B981',
          rose: '#FF4D6D',
          surface: '#F4F1EA',
        },
        fp: {
          ink: '#0F172A',
          paper: '#FFFDF7',
          canvas: '#F4F1EA',
          mint: '#00D68F',
          emerald: '#10B981',
          amber: '#FACC15',
          coral: '#FF4D6D',
          cobalt: '#3B82F6',
          violet: '#A855F7',
        },
      },
      fontFamily: {
        sans: ['Quicksand', 'ui-sans-serif', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'sans-serif'],
        mono: ['"Space Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
      boxShadow: {
        brutal: '4px 4px 0px 0px #0F172A',
        'brutal-sm': '2px 2px 0px 0px #0F172A',
        'brutal-lg': '6px 6px 0px 0px #0F172A',
        'brutal-mint': '4px 4px 0px 0px #00D68F',
        'brutal-coral': '4px 4px 0px 0px #FF4D6D',
        'brutal-amber': '4px 4px 0px 0px #FACC15',
      },
    },
  },
  plugins: [],
};
