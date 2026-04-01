import { useState } from 'react';
import SearchBar from '../components/SearchBar';
import ProductCard from '../components/ProductCard';
import { useProductSearch } from '../hooks/useProductSearch';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const { data, isLoading, error } = useProductSearch(query);

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 py-4 flex flex-col items-center gap-3">
          <h1 className="text-xl font-bold text-indigo-700 tracking-tight">
            ItemFinder — 패션 가격 비교
          </h1>
          <SearchBar onSearch={setQuery} />
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-4 py-8">
        {!query && (
          <p className="text-center text-gray-400 mt-20 text-sm">
            검색어를 입력하세요 (예: 아디다스, 나이키 에어포스)
          </p>
        )}

        {query.length > 0 && query.length < 2 && (
          <p className="text-center text-gray-400 mt-20 text-sm">두 글자 이상 입력하세요</p>
        )}

        {isLoading && (
          <div className="flex justify-center mt-20">
            <div className="w-8 h-8 border-4 border-indigo-300 border-t-indigo-600 rounded-full animate-spin" />
          </div>
        )}

        {error && (
          <p className="text-center text-red-500 mt-20 text-sm">
            오류가 발생했습니다. 서버가 실행 중인지 확인하세요.
          </p>
        )}

        {data && data.length === 0 && (
          <p className="text-center text-gray-400 mt-20 text-sm">
            "{query}"에 대한 검색 결과가 없습니다.
            <br />
            <span className="text-xs">크롤링 후 다시 검색해 보세요.</span>
          </p>
        )}

        {data && data.length > 0 && (
          <>
            <p className="text-xs text-gray-400 mb-4">{data.length}개 상품</p>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
              {data.map(product => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
