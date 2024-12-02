import React from "react";
import "./Navbar.css";
import { Link } from "react-router-dom";
import ImageSvg from "./RNAtive.svg";
import logo from "./RNAtive2.svg";
const Navbar = () => {
  return (
    <nav className="navbar">
      <div className="navbar-left">
        <div style={{ width: "10%", textAlign: "center" }}>
          <img src={logo} alt="RNAtive" style={{ height: "32px" }} />
        </div>
        {/* <a href="/" className="logo">
          RNAtive
        </a> */}
      </div>
      <div className="navbar-center">
        <ul className="nav-links">
          <li>
            <Link to="/">Home</Link>
          </li>
          <li>
            <Link to="/Onas">About us</Link>
          </li>
          <li>
            <Link to="/Help">Help</Link>
          </li>
          <li>
            <Link to="/Cite">Cite</Link>
          </li>
        </ul>
      </div>
      <div></div>
    </nav>
  );
};

export default Navbar;
