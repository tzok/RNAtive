export const getTableColumns = (headers, rows) => {
  return headers
    .map((header, index) => {
      // Skip "Is reference?" column if all values are empty
      if (header === "Is reference?" && rows.every((row) => !row[index])) {
        return null;
      }

      return {
        title: header,
        dataIndex: index,
        key: index,
        sorter: (a, b) => {
          const valA = a[index];
          const valB = b[index];
          // Handle numeric values
          if (!isNaN(valA) && !isNaN(valB)) {
            return valA - valB;
          }
          // Handle string values
          return String(valA).localeCompare(String(valB));
        },
        render: (text) => {
          // Handle "Is reference?" column
          if (header === "Is reference?" && text) {
            return "✓";
          }
          // Keep Rank as integer, format other numbers to 3 decimal places
          if (!isNaN(text)) {
            return header === "Rank" ? Number(text) : Number(text).toFixed(3);
          }
          return text;
        },
      };
    })
    .filter(Boolean);
};

export const getTableRows = (rows) => {
  return rows.map((row, index) => ({
    ...row,
    key: index,
  }));
};
