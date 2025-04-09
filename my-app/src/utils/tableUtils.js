const decimalToFraction = (decimal, denominator) => {
  const numerator = Math.round(decimal * denominator);
  return `${numerator} / ${denominator}`;
};

export const getTableColumns = (headers, rows, fileCount = 100) => {
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
          // Handle boolean columns for reference pairing
          if (
            (header === "Paired in reference" ||
              header === "Unpaired in reference") &&
            typeof text === "boolean"
          ) {
            return text ? "✓" : "";
          }
          // Handle different number formats
          if (typeof text === "number" && !isNaN(text)) {
            if (header === "Rank") {
              return Number(text);
            }
            if (header === "Confidence") {
              return decimalToFraction(Number(text), fileCount);
            }
            return Number(text).toFixed(3);
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
