import React, { useState, useEffect } from "react";
import "./SvgImg.css";
import { Image } from "antd";

const SvgImg = ({ serverAddress, taskId }) => {
  const [svgContent, setSvgContent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchSvg = async () => {
      try {
        const response = await fetch(`${serverAddress}/${taskId}/svg`, {
          method: "GET",
        });

        if (!response.ok) {
          throw new Error(`Failed to fetch SVG. Status: ${response.status}`);
        }

        const svgText = await response.text();
        setSvgContent(svgText);
        setLoading(false);
      } catch (err) {
        console.error("Error fetching SVG:", err.message);
        setError(err.message);
        setLoading(false);
      }
    };

    fetchSvg();
  }, [serverAddress, taskId]);

  if (loading) {
    return (
      <div className="loading-indicator">
        <div className="spinner"></div> {/* Add a spinner style in your CSS */}
        <p>Loading SVG...</p>
      </div>
    );
  }

  if (error) {
    return <p className="error">Error: {error}</p>;
  }
  // Create a blob for the SVG content to use for downloading
  const blob = new Blob([svgContent], { type: "image/svg+xml" });
  const blobUrl = URL.createObjectURL(blob);

  return (
    <Image src={blobUrl} width={200} />
    // <div style={{ backgroundColor: "white", padding: "20px", borderRadius: "5px" }}>
    //   <a href={blobUrl} download={`${taskId}.svg`} className="svg-download-link" title="Right-click to download">
    //     <div
    //       className="svg-container"
    //       style={{
    //         width: "15vw",
    //         height: "auto",
    //       }}
    //       dangerouslySetInnerHTML={{ __html: svgContent }}
    //     />
    //   </a>
    // </div>
  );
};

export default SvgImg;
