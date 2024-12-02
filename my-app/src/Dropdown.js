import React from "react";
import "./Dropdown.css";

const Dropdown = ({ options = [], value, onSelect }) => {
  const handleChange = (e) => {
    onSelect(e.target.value); // Notify parent of the selected value
  };

  return (
    <div className="dropdown-container">
      <select value={value} onChange={handleChange} className="dropdown">
        {options.map((option, index) => (
          <option key={index} value={option}>
            {option}
          </option>
        ))}
      </select>
    </div>
  );
};

export default Dropdown;
