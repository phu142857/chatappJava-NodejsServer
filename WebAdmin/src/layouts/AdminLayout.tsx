import { Layout, Menu, Breadcrumb, Avatar, Dropdown, Typography } from 'antd';
import { UserOutlined, DashboardOutlined, LogoutOutlined, MessageOutlined, PhoneOutlined, TeamOutlined, UserAddOutlined, SecurityScanOutlined, BarChartOutlined } from '@ant-design/icons';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { STORAGE_KEYS } from '../config';

const { Header, Sider, Content } = Layout;

export default function AdminLayout() {
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    localStorage.removeItem(STORAGE_KEYS.accessToken);
    navigate('/login');
  };

  const selectedKey = location.pathname === '/' ? '/' : `/${location.pathname.split('/')[1]}`;

  const menuItems = [
    { key: '/', icon: <DashboardOutlined />, label: <Link to="/">Dashboard</Link> },
    { key: '/users', icon: <UserOutlined />, label: <Link to="/users">Users</Link> },
    { key: '/chats', icon: <MessageOutlined />, label: <Link to="/chats">Chats & Messages</Link> },
    { key: '/groups', icon: <TeamOutlined />, label: <Link to="/groups">Groups</Link> },
    { key: '/friend-requests', icon: <UserAddOutlined />, label: <Link to="/friend-requests">Friend Requests</Link> },
    { key: '/calls', icon: <PhoneOutlined />, label: <Link to="/calls">Calls</Link> },
    { key: '/security', icon: <SecurityScanOutlined />, label: <Link to="/security">Security</Link> },
    { key: '/statistics', icon: <BarChartOutlined />, label: <Link to="/statistics">Statistics</Link> },
  ];

  const breadcrumbItems = location.pathname
    .split('/').filter(Boolean)
    .map((seg, idx, arr) => {
      const url = '/' + arr.slice(0, idx + 1).join('/');
      const label = seg.charAt(0).toUpperCase() + seg.slice(1);
      return { key: url, title: <Link to={url}>{label}</Link> };
    });

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsible>
        <div style={{ height: 48, margin: 16, color: '#fff', fontWeight: 700, display: 'flex', alignItems: 'center' }}>WebAdmin</div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]} items={menuItems} />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', padding: '0 16px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Breadcrumb items={[{ key: 'home', title: <Link to="/">Home</Link> }, ...breadcrumbItems]} />
          <Dropdown
            menu={{
              items: [
                { key: 'logout', label: <span onClick={handleLogout} style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><LogoutOutlined /> Logout</span> },
              ],
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }}>
              <Avatar size={32} icon={<UserOutlined />} />
              <Typography.Text strong>Admin</Typography.Text>
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: '16px', padding: 16 }}>
          <div style={{ background: '#fff', minHeight: 'calc(100vh - 160px)', padding: 16 }}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}


