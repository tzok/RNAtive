import React, { useState } from "react";
import "./ResultTable.css";

const ResultTable = ({ ranking }) => {
  const { headers, rows } = ranking;
  const [currentPage, setCurrentPage] = useState(0);
  const rowsPerPage = 5;

  const startIndex = currentPage * rowsPerPage;
  const currentRows = rows.slice(startIndex, startIndex + rowsPerPage);

  const handlePrevPage = () => {
    if (currentPage > 0) {
      setCurrentPage(currentPage - 1);
    }
  };

  const handleNextPage = () => {
    if (startIndex + rowsPerPage < rows.length) {
      setCurrentPage(currentPage + 1);
    }
  };

  return (
    <div className="result-table-container">
      <table className="result-table">
        <thead>
          <tr>
            {headers.map((header, index) => (
              <th key={index}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {currentRows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {row.map((cell, cellIndex) => (
                <td key={cellIndex}>{cell}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length > rowsPerPage && (
        <div className="pagination">
          <button onClick={handlePrevPage} disabled={currentPage === 0} className="pagination-btn">
            ◀ Prev
          </button>
          <span className="page-info">
            Page {currentPage + 1} of {Math.ceil(rows.length / rowsPerPage)}
          </span>
          <button onClick={handleNextPage} disabled={startIndex + rowsPerPage >= rows.length} className="pagination-btn">
            Next ▶
          </button>
        </div>
      )}
    </div>
  );
};

export default ResultTable;
