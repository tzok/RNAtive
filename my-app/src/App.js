import { BrowserRouter as Router, Route, Routes, useNavigate, useLocation } from "react-router-dom";
import { Layout, Menu } from "antd";

import About from "./About";
import Home from "./Home";
import Help from "./Help";
import Cite from "./Cite";
import LogoPP from "./pp-logo.png";
import LogoIBCh from "./ibch-logo.png";
import LogoRNApolis from "./rnapolis-logo.png";
import LogoRNAtive from "./RNAtive.svg";

const { Header, Footer, Content } = Layout;

const NavMenu = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const handleMenuClick = ({ key }) => {
    if (key === "/") {
      // Force a reload when navigating to home to reset the state
      window.location.href = "/";
    } else {
      navigate(key);
    }
  };

  const items = [
    { key: "/", label: "Home" },
    { key: "/about", label: "About" },
    { key: "/help", label: "Help" },
    { key: "/cite", label: "Cite" },
  ];

  return <Menu onClick={handleMenuClick} selectedKeys={[location.pathname]} mode="horizontal" items={items} />;
};

function App() {
  return (
    <Router>
      <Layout>
        <Header
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            padding: "0 32px",
            background: "white",
            boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
          }}>
          <img
            src={LogoRNAtive}
            alt="RNAtive"
            style={{
              height: "64px",
              padding: "10px",
              cursor: "pointer",
            }}
            onClick={() => {
              window.location.href = "/";
            }}
          />
          <NavMenu />
        </Header>
        <Content
          style={{
            padding: "24px",
          }}>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/:id" element={<Home />} />
            <Route path="/About" element={<About />} />
            <Route path="/Help" element={<Help />} />
            <Route path="/Cite" element={<Cite />} />
          </Routes>
        </Content>
        <Footer
          style={{
            textAlign: "center",
          }}>
          <div
            style={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              gap: "16px",
            }}>
            <div>
              <a href="https://www.put.poznan.pl/index.php/en">
                <img src={LogoPP} alt="PUT" style={{ height: "86px" }} />
              </a>
            </div>
            <div>
              <a href="https://www.rnapolis.pl/">
                <img src={LogoRNApolis} alt="RNApolis" style={{ height: "40px" }} />
              </a>
            </div>
            <div>
              <a href="https://www.ibch.poznan.pl/en.html">
                <img src={LogoIBCh} alt="IBcH PAS" style={{ height: "96px" }} />
              </a>
            </div>
          </div>
          <div style={{ marginTop: "16px" }}>
            <p style={{ margin: "0" }}>RNAtive is freely accessible to all users, including commercial users, under the MIT License.</p>
            <p style={{ margin: "8px 0" }}>RNAtive {new Date().getFullYear()} | RNApolis</p>
          </div>
        </Footer>
      </Layout>
    </Router>
  );
}

export default App;
