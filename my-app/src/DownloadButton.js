import { Button } from "antd";

const DownloadButton = ({ dataSource, columns, fileName }) => {
  const handleDownload = () => {
    let content = columns.map((col) => col.title).join("\t") + "\n"; // Header row
    content += dataSource
      .map((row) => columns.map((col) => row[col.dataIndex]).join("\t"))
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
{
  /* example use:
    <div>
            <Table columns={columns} dataSource={dataSource} />
            <DownloadButton
              dataSource={dataSource}
              columns={columns}
              fileName={`my_table_${dataSource.length}.txt`}
            />
          </div> 
          */
}
