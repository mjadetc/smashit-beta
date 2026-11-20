import { useCallback, useEffect, useState } from 'react';

import { api } from '../api/client.js';

// A "custom hook": a function starting with "use" that other components can share.
// This one loads data from the backend when a page opens:
//
//   const { data: event, loading, error, setData } = useApiData('/api/events/5');
//
//   loading = true until the answer arrives
//   data    = the JSON answer (null while loading or after an error)
//   error   = the ApiError if it failed (e.g. status 404), otherwise null
//   setData = replace the data yourself, e.g. with the event a "Complete" button returned,
//             so the page updates without loading everything again (see setData below)
//   reload  = ask the backend again (NEW in step 4). The old data stays on screen
//             until the new answer arrives, so the page doesn't flash "Loading…".
//
// When `path` changes (e.g. switching from ?status=ACTIVE to ?status=COMPLETED),
// it loads again.
export function useApiData(path) {
  // We remember WHICH path the result belongs to. While the result is for an older
  // path, the new one is still loading. (This avoids a separate "loading" state that
  // would have to be switched on at the start of every load.)
  const [result, setResult] = useState({ path: null, data: null, error: null });
  // A counter: every reload() adds 1, and the effect below runs again when it changes.
  const [version, setVersion] = useState(0);

  useEffect(() => {
    // Same trick as in AuthProvider: if the page closes or the path changes before the
    // answer arrives, ignore that old answer.
    let cancelled = false;

    api(path).then(
      (data) => {
        if (!cancelled) setResult({ path, data, error: null });
      },
      (error) => {
        if (!cancelled) setResult({ path, data: null, error });
      },
    );

    return () => {
      cancelled = true;
    };
  }, [path, version]); // run again whenever path changes, or reload() is called

  // useCallback keeps the same function between renders, which is safe to pass around.
  // Like a useState setter, it takes a new value OR a function that gets the current
  // data and returns the new one (NEW in step 4), e.g.
  //   setData((matches) => [...matches, newMatch])
  // The function form always sees the latest data, even if two changes overlap.
  const setData = useCallback((dataOrUpdate) => {
    setResult((current) => ({
      ...current,
      data: typeof dataOrUpdate === 'function' ? dataOrUpdate(current.data) : dataOrUpdate,
    }));
  }, []);

  const reload = useCallback(() => {
    setVersion((current) => current + 1);
  }, []);

  const loading = result.path !== path;
  return {
    loading,
    data: loading ? null : result.data,
    error: loading ? null : result.error,
    setData,
    reload,
  };
}