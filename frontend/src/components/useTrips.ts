import { useCallback, useEffect, useState } from 'react';
import { apiService } from '../services/api';
import type { Trip } from '../services/api';

/** Loads the trips once on mount; call `reload` after changing them. */
export function useTrips() {
    const [trips, setTrips] = useState<Trip[]>([]);
    const [loading, setLoading] = useState(true);

    const reload = useCallback(async () => {
        try {
            setTrips(await apiService.getTrips());
        } catch (e) {
            console.error('Error loading trips:', e);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { reload(); }, [reload]);

    return { trips, loading, reload };
}
