import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';

import App from './App.jsx';
import './index.css';

// The starting point: draw <App /> inside the <div id="root"> of index.html.
// StrictMode = extra checks during development only (it runs some code twice to find bugs).
// BrowserRouter = keeps the page in sync with the address bar (/login, /events...).
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
);