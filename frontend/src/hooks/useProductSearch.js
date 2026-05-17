import { useState, useEffect, useRef } from 'react';
import { getSearchStreamUrl } from '../api/productApi';

export function useProductSearch(keyword) {
  const [products, setProducts] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const [failedPlatforms, setFailedPlatforms] = useState([]);
  const esRef = useRef(null);

  useEffect(() => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }

    if (!keyword || keyword.length < 2) {
      setProducts([]);
      setIsLoading(false);
      setError(null);
      setFailedPlatforms([]);
      return;
    }

    setProducts([]);
    setError(null);
    setFailedPlatforms([]);
    setIsLoading(true);

    const url = getSearchStreamUrl(keyword);
    const es = new EventSource(url);
    esRef.current = es;

    es.addEventListener('data', (e) => {
      try {
        const newProducts = JSON.parse(e.data);
        setProducts(prev => [...prev, ...newProducts]);
      } catch (err) {
        console.error('Failed to parse SSE data:', err);
      }
    });

    es.addEventListener('done', (e) => {
      setIsLoading(false);
      try {
        const { failedPlatforms: failed } = JSON.parse(e.data);
        if (failed?.length > 0) setFailedPlatforms(failed);
      } catch {
        // done 데이터 파싱 실패 시 무시
      }
      es.close();
      esRef.current = null;
    });

    es.addEventListener('error', (e) => {
      setError('검색 중 오류가 발생했습니다.');
      setIsLoading(false);
      es.close();
      esRef.current = null;
    });

    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED) return;
      setError('서버 연결에 실패했습니다. 서버가 실행 중인지 확인하세요.');
      setIsLoading(false);
      es.close();
      esRef.current = null;
    };

    return () => {
      if (esRef.current) {
        esRef.current.close();
        esRef.current = null;
      }
    };
  }, [keyword]);

  return { data: products, isLoading, error, failedPlatforms };
}
