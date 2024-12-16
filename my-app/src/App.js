import logo from "./logo.svg";
import "./App.css";
import Navbar from "./Navbar";
import Footer from "./Footer";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import Onas from "./Onas";
import Home from "./Home";
import Help from "./Help";
import Cite from "./Cite";

import SendingTest from "./SendingTest";
function App() {
  return (
    <Router>
      <Navbar />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/:id" element={<Home />} />
        <Route path="/Onas" element={<Onas />} />
        <Route path="/Help" element={<Help />} />
        <Route path="/Cite" element={<Cite />} />
        {/* Add more routes as needed */}
      </Routes>
      <Footer />
    </Router>
  );
}

export default App;

/*
<Route path="/" element={<Home />} />
  <Route path="/Home/" element={<Home />} />
  <Route path="/Home/:id" element={<Home />} />
*/
