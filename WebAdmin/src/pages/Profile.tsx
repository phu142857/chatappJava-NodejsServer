import { useEffect, useState } from 'react';
import { App as AntdApp, Avatar, Button, Card, Form, Input, Modal, Space, Typography } from 'antd';
import { UserOutlined, LockOutlined, DeleteOutlined } from '@ant-design/icons';
import apiClient from '../api/client';

const { Title, Text } = Typography;

interface UserProfile {
  _id: string;
  username: string;
  email: string;
  avatar?: string;
  role: string;
  isActive: boolean;
  profile?: {
    firstName?: string;
    lastName?: string;
  };
  createdAt: string;
  lastSeen?: string;
}

export default function Profile() {
  const { modal, message } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [editForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);

  useEffect(() => {
    fetchUserProfile();
  }, []);

  const fetchUserProfile = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get('/auth/me');
      setUser(response.data.data.user);
    } catch (error) {
      console.error('Failed to fetch user profile:', error);
      message.error('Failed to load profile');
    } finally {
      setLoading(false);
    }
  };

  const handleEditProfile = async (values: any) => {
    try {
      setLoading(true);
      await apiClient.put('/auth/profile', values);
      message.success('Profile updated successfully');
      setEditModalOpen(false);
      fetchUserProfile();
    } catch (error: any) {
      message.error(error?.response?.data?.message || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (values: any) => {
    try {
      setLoading(true);
      await apiClient.put('/auth/change-password', values);
      message.success('Password changed successfully');
      setPasswordModalOpen(false);
      passwordForm.resetFields();
    } catch (error: any) {
      message.error(error?.response?.data?.message || 'Failed to change password');
    } finally {
      setLoading(false);
    }
  };

  const handleChangeRole = async (newRole: string) => {
    try {
      setLoading(true);
      const response = await apiClient.put('/users/me/role', { role: newRole });
      
      if (response.data.requireReauth) {
        // User changed their own role, force logout
        message.success('Role updated successfully. Please log in again to apply changes.');
        localStorage.clear();
        window.location.href = '/login';
      } else {
        message.success('Role updated successfully');
        fetchUserProfile();
      }
    } catch (error: any) {
      message.error(error?.response?.data?.message || 'Failed to update role');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteAccount = () => {
    modal.confirm({
      title: 'Delete Account',
      content: 'Are you sure you want to delete your account? This action cannot be undone.',
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          setLoading(true);
          await apiClient.delete('/auth/me');
          message.success('Account deleted successfully');
          localStorage.clear();
          window.location.href = '/login';
        } catch (error: any) {
          message.error(error?.response?.data?.message || 'Failed to delete account');
        } finally {
          setLoading(false);
        }
      }
    });
  };

  if (!user) {
    return <Card loading={loading}><div>Loading...</div></Card>;
  }

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '24px' }}>
      <Title level={2}>My Profile</Title>
      
      <Card>
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 24 }}>
          <Avatar 
            size={80} 
            src={user.avatar} 
            icon={<UserOutlined />}
            style={{ marginRight: 16 }}
          />
          <div>
            <Title level={3} style={{ margin: 0 }}>{user.username}</Title>
            <Text type="secondary">{user.email}</Text>
            <br />
            <Space>
              <Text type="secondary">Role: {user.role}</Text>
              {user.role !== 'admin' && (
                <Button 
                  size="small" 
                  onClick={() => {
                    modal.confirm({
                      title: 'Change Role',
                      content: 'Are you sure you want to change your role? You will be logged out and need to log in again.',
                      okText: 'Change Role',
                      cancelText: 'Cancel',
                      onOk: () => handleChangeRole(user.role === 'user' ? 'moderator' : 'user')
                    });
                  }}
                >
                  Change to {user.role === 'user' ? 'Moderator' : 'User'}
                </Button>
              )}
            </Space>
          </div>
        </div>

        <div style={{ marginBottom: 24 }}>
          <Title level={4}>Personal Information</Title>
          <div style={{ marginBottom: 8 }}>
            <Text strong>Name: </Text>
            <Text>{user.profile?.firstName || 'N/A'} {user.profile?.lastName || 'N/A'}</Text>
          </div>
          <div style={{ marginBottom: 8 }}>
            <Text strong>Username: </Text>
            <Text>{user.username}</Text>
          </div>
          <div style={{ marginBottom: 8 }}>
            <Text strong>Email: </Text>
            <Text>{user.email}</Text>
          </div>
          <div style={{ marginBottom: 8 }}>
            <Text strong>Account Status: </Text>
            <Text type={user.isActive ? 'success' : 'danger'}>
              {user.isActive ? 'Active' : 'Inactive'}
            </Text>
          </div>
          <div style={{ marginBottom: 8 }}>
            <Text strong>Member Since: </Text>
            <Text>{new Date(user.createdAt).toLocaleDateString()}</Text>
          </div>
          {user.lastSeen && (
            <div style={{ marginBottom: 8 }}>
              <Text strong>Last Seen: </Text>
              <Text>{new Date(user.lastSeen).toLocaleString()}</Text>
            </div>
          )}
        </div>

        <Space>
          <Button 
            type="primary" 
            icon={<UserOutlined />}
            onClick={() => {
              setEditModalOpen(true);
              editForm.setFieldsValue({
                username: user.username,
                firstName: user.profile?.firstName || '',
                lastName: user.profile?.lastName || ''
              });
            }}
          >
            Edit Profile
          </Button>
          
          <Button 
            icon={<LockOutlined />}
            onClick={() => setPasswordModalOpen(true)}
          >
            Change Password
          </Button>
          
          <Button 
            danger 
            icon={<DeleteOutlined />}
            onClick={handleDeleteAccount}
          >
            Delete Account
          </Button>
        </Space>
      </Card>

      {/* Edit Profile Modal */}
      <Modal
        title="Edit Profile"
        open={editModalOpen}
        onCancel={() => setEditModalOpen(false)}
        onOk={() => editForm.submit()}
        confirmLoading={loading}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={handleEditProfile}
        >
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true, message: 'Please enter username' }]}
          >
            <Input />
          </Form.Item>
          
          <Form.Item
            name="firstName"
            label="First Name"
          >
            <Input />
          </Form.Item>
          
          <Form.Item
            name="lastName"
            label="Last Name"
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* Change Password Modal */}
      <Modal
        title="Change Password"
        open={passwordModalOpen}
        onCancel={() => {
          setPasswordModalOpen(false);
          passwordForm.resetFields();
        }}
        onOk={() => passwordForm.submit()}
        confirmLoading={loading}
      >
        <Form
          form={passwordForm}
          layout="vertical"
          onFinish={handleChangePassword}
        >
          <Form.Item
            name="currentPassword"
            label="Current Password"
            rules={[{ required: true, message: 'Please enter current password' }]}
          >
            <Input.Password />
          </Form.Item>
          
          <Form.Item
            name="newPassword"
            label="New Password"
            rules={[
              { required: true, message: 'Please enter new password' },
              { min: 6, message: 'Password must be at least 6 characters' }
            ]}
          >
            <Input.Password />
          </Form.Item>
          
          <Form.Item
            name="confirmPassword"
            label="Confirm New Password"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Please confirm new password' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Passwords do not match'));
                },
              }),
            ]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
