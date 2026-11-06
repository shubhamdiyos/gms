import { configureStore } from '@reduxjs/toolkit';
import authSlice from './slices/authSlice';
import uiSlice from './slices/uiSlice';
import { apiSlice, sessionExpiryMiddleware } from './api/apiSlice';

export const store = configureStore({
  reducer: {
    auth: authSlice,
    ui: uiSlice,
    api: apiSlice.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActions: ['persist/PERSIST', 'persist/REHYDRATE'],
      },
    })
      .concat(apiSlice.middleware)
      .concat(sessionExpiryMiddleware),
  // RTK Query marks its own actions to skip the store's synchronous notification pass and lets
  // `autoBatchEnhancer` deliver one batched notification afterwards. That enhancer defaults to
  // `requestAnimationFrame`, and browsers pause rAF whenever the document is not being painted — a
  // background tab, an occluded window, or an embedded/headless webview — so a response that landed
  // while the page was hidden left every screen on its loading state until something forced a
  // frame. `tick` keeps the single batched notification but delivers it on the microtask queue, so
  // data surfaces whether or not the page is on screen.
  enhancers: (getDefaultEnhancers) => getDefaultEnhancers({ autoBatch: { type: 'tick' } }),
});

export default store;
