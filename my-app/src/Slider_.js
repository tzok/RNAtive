import React, { useState, useEffect } from "react";
import "./Slider.css";

const Slider_ = ({ value = 0.5, onValueChange }) => {
  const [sliderValue, setSliderValue] = useState(value);

  // Update sliderValue when prop 'value' changes
  useEffect(() => {
    setSliderValue(value);
  }, [value]);

  // Handle slider change
  const handleSliderChange = (e) => {
    const newValue = parseFloat(e.target.value);
    setSliderValue(newValue);
    onValueChange(newValue); // Update the main App
  };

  // Handle text input change
  const handleInputChange = (e) => {
    let newValue = parseFloat(e.target.value);
    if (isNaN(newValue) || newValue < 0) newValue = 0;
    if (newValue > 1) newValue = 1;
    setSliderValue(newValue);
    onValueChange(newValue); // Update the main App
  };

  return (
    <div className="slider-container">
      <input
        type="range"
        min="0"
        max="1"
        step="0.01"
        value={sliderValue}
        onChange={handleSliderChange}
        className="slider"
        style={{
          "--slider-value": `${sliderValue * 100}%`, // Selected track percentage width
        }}
      />
      <input type="number" min="0" max="1" step="0.01" value={sliderValue} onChange={handleInputChange} className="slider-input" />
    </div>
  );
};

export default Slider_;
