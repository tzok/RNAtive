import React, { useState, useEffect } from "react";
import { Alert, Image, Spin } from "antd";

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
    return <Spin />;
  }
  if (error) {
    return <Alert type={"error"} message={error.message} />;
  }

  // Create a blob for the SVG content to use for downloading
  const blob = new Blob([svgContent], { type: "image/svg+xml" });
  const blobUrl = URL.createObjectURL(blob);

  return <Image src={blobUrl} style={{ width: "60vh" }} />;
};

export default SvgImg;
