import { useState } from 'react';

export default function SearchBar({ onSearch }) {
  const [value, setValue] = useState('');

  const handleChange = (e) => {
    setValue(e.target.value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSearch(value);
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2 w-full max-w-2xl">
      <input
        type="text"
        value={value}
        onChange={handleChange}
        placeholder="상품명 또는 브랜드 검색... (예: 아디다스 스탠스미스)"
        className="flex-1 px-4 py-3 rounded-xl border border-gray-300 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-400 bg-white"
      />
      <button
        type="submit"
        className="px-5 py-3 bg-indigo-600 text-white text-sm rounded-xl hover:bg-indigo-700 transition-colors"
      >
        검색
      </button>
    </form>
  );
}
