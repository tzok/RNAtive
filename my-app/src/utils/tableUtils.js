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
            if (header === "Confidence") {
              return (
                decimalToFraction(Number(text), fileCount) +
                " (" +
                String((Number(text) * 100).toFixed(0)) +
                "%)"
              );
            }
            return Number(text).toFixed(3);
          }
          return text;
        },
      };
    })
    .filter(Boolean);
};

export const getGroupedRankingTableColumns = (headers, rows) => {
  if (!headers || headers.length === 0) {
    return [];
  }

  const groupedColumns = [];

  // First column is always "File name"
  groupedColumns.push({
    title: headers[0],
    dataIndex: 0,
    key: 0,
    sorter: (a, b) => String(a[0]).localeCompare(String(b[0])),
    fixed: 'left', // Optional: if table becomes too wide
  });

  // Process the rest of the headers which come in triplets
  for (let i = 1; i < headers.length; i += 3) {
    const rankHeader = headers[i]; // e.g., "Rank (ALL)"

    // Extract mode from header, e.g., "ALL" from "Rank (ALL)"
    const modeMatch = rankHeader.match(/\(([^)]+)\)/);
    if (!modeMatch) continue; // Should not happen with expected format
    const mode = modeMatch[1];

    const children = [
      {
        title: "Rank",
        dataIndex: i,
        key: i,
        sorter: (a, b) => {
          const valA = parseFloat(a[i]);
          const valB = parseFloat(b[i]);
          return valA - valB;
        },
        render: (text) => {
          if (text !== null && text !== undefined && !isNaN(parseFloat(text))) {
            return Math.round(parseFloat(text));
          }
          return text;
        },
        ...(mode === "All" && { defaultSortOrder: "ascend" }),
      },
      {
        title: "INF",
        dataIndex: i + 1,
        key: i + 1,
        sorter: (a, b) => {
          const valA = parseFloat(a[i + 1]);
          const valB = parseFloat(b[i + 1]);
          return valA - valB;
        },
        render: (text) => {
          if (typeof text === "number" && !isNaN(text)) {
            return Number(text).toFixed(3);
          }
          return text;
        },
      },
      {
        title: "F1",
        dataIndex: i + 2,
        key: i + 2,
        sorter: (a, b) => {
          const valA = parseFloat(a[i + 2]);
          const valB = parseFloat(b[i + 2]);
          return valA - valB;
        },
        render: (text) => {
          if (typeof text === "number" && !isNaN(text)) {
            return Number(text).toFixed(3);
          }
          return text;
        },
      },
    ];

    groupedColumns.push({
      title: mode,
      children: children,
    });
  }

  return groupedColumns;
};

export const getTableRows = (rows) => {
  return rows.map((row, index) => ({
    ...row,
    key: index,
  }));
};
