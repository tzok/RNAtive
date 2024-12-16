import React from "react";
import "./Navbar.css";
import { Link } from "react-router-dom";
import ImageSvg from "./RNAtive.svg";
import logo from "./RNAtive2.svg";
const Footer = () => {
  return (
    <nav className="navbar">
      <div className="navbar-left">
        {/* <div style={{ width: "10%", textAlign: "center" }}>
          <img src={logo} alt="RNAtive" style={{ height: "32px" }} />
        </div> */}
        {/* <a href="/" className="logo">
          RNAtive
        </a> */}
      </div>
      <div className="navbar-center">
        <p>
          This website is free to use. We do not require any cookies or login to
          access it.
        </p>
      </div>
      <div></div>
    </nav>
  );
};

export default Footer;
