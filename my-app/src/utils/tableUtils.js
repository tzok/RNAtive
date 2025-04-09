const decimalToFraction = (decimal, denominator) => {
  const numerator = Math.round(decimal * denominator);
  return `${numerator} / ${denominator}`;
};

export const getTableColumns = (headers, rows, fileCount = 100) => {
  // Find indices for the reference columns
  const pairedIndex = headers.indexOf("Paired in reference");
  const unpairedIndex = headers.indexOf("Unpaired in reference");

  // Check if any row has a true value in either reference column
  let showReferenceColumns = false;
  if (pairedIndex !== -1 || unpairedIndex !== -1) {
    showReferenceColumns = rows.some(
      (row) =>
        (pairedIndex !== -1 && row[pairedIndex]) ||
        (unpairedIndex !== -1 && row[unpairedIndex])
    );
  }

  return headers
    .map((header, index) => {
      // Skip reference columns if no row has true in either
      if (
        !showReferenceColumns &&
        (header === "Paired in reference" || header === "Unpaired in reference")
      ) {
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
