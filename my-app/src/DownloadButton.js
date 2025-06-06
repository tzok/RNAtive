import { Button } from "antd";

const DownloadButton = ({ dataSource, columns, fileName }) => {
  // Helper function to flatten columns and create appropriate download titles
  const getLeafColumnsWithTitles = (cols) => {
    const flatColumns = [];
    cols.forEach((col) => {
      if (col.children && col.children.length > 0) {
        // This is a group column
        col.children.forEach((childCol) => {
          flatColumns.push({
            // Construct title like "Rank (ALL)" from child title "Rank" and parent title "ALL"
            downloadTitle: `${childCol.title} (${col.title})`,
            dataIndex: childCol.dataIndex,
          });
        });
      } else {
        // This is a leaf column (or a flat column like "File name")
        flatColumns.push({
          downloadTitle: col.title,
          dataIndex: col.dataIndex,
        });
      }
    });
    return flatColumns;
  };

  const handleDownload = () => {
    const flatDownloadColumns = getLeafColumnsWithTitles(columns);

    let content =
      flatDownloadColumns.map((col) => col.downloadTitle).join("\t") + "\n"; // Header row

    content += dataSource
      .map((row) =>
        flatDownloadColumns.map((col) => row[col.dataIndex]).join("\t")
      )
      .join("\n");

    const blob = new Blob([content], { type: "text/plain" });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = fileName || "table.txt";
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return <Button onClick={handleDownload}>Download</Button>;
};

export default DownloadButton;
