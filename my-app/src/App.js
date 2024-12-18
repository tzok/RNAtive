import LogoPP from "./pp-logo.png";
import LogoIBCh from "./ibch-logo.png";
import LogoRNApolis from "./rnapolis-logo.png";
import LogoRNAtive from "./RNAtive3.svg";
import "./App.css";
import { BrowserRouter as Router, Route, Routes, useNavigate, useLocation } from "react-router-dom";
import About from "./About";
import Home from "./Home";
import Help from "./Help";
import Cite from "./Cite";
import { Layout, Menu } from "antd";

const { Header, Footer, Sider, Content } = Layout;

const NavMenu = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  return (
    <Menu onClick={handleMenuClick} selectedKeys={[location.pathname]} mode="horizontal">
      <Menu.Item key="/">Home</Menu.Item>
      <Menu.Item key="/about">About</Menu.Item>
      <Menu.Item key="/help">Help</Menu.Item>
      <Menu.Item key="/cite">Cite</Menu.Item>
    </Menu>
  );
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
            {/* Add more routes as needed */}
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
          <p>RNAtive {new Date().getFullYear()} | RNApolis</p>
        </Footer>
      </Layout>
    </Router>
  );
}

export default App;

/*
<Route path="/" element={<Home />} />
  <Route path="/Home/" element={<Home />} />
  <Route path="/Home/:id" element={<Home />} />
*/
