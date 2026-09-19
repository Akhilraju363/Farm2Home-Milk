/* eslint-env node */
// Baseline ESLint config for the Farm2Home frontend.
// The codebase predates any lint setup, so `@typescript-eslint/no-explicit-any`
// is left off for now (tracked as tech debt) rather than forcing a wide refactor.
// Everything else in the recommended sets is enforced so regressions are caught.
module.exports = {
  root: true,
  env: { browser: true, es2020: true, node: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  plugins: ['@typescript-eslint', 'react-hooks'],
  ignorePatterns: ['dist', 'node_modules', 'coverage', 'vite.config.ts', '*.cjs'],
  rules: {
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/no-unused-vars': [
      'error',
      { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
    ],
  },
}
