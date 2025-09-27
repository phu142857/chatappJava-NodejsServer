import { useState } from 'react';
import { Button, Card, Form, Input, message, Typography } from 'antd';
import apiClient from '../api/client';
import { STORAGE_KEYS } from '../config';
import { useNavigate } from 'react-router-dom';

type LoginResponse = {
  success: boolean;
  message?: string;
  data?: {
    token: string;
    user: unknown;
  };
};

export default function Login() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);
    try {
      const { data } = await apiClient.post<LoginResponse>('/auth/login', values);
      if (data?.success && data?.data?.token) {
        localStorage.setItem(STORAGE_KEYS.accessToken, data.data.token);
        message.success('Login successful');
        navigate('/');
      } else {
        message.error(data?.message || 'Login failed');
      }
    } catch (err: unknown) {
      message.error('Incorrect email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>Admin Login</Typography.Title>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item label="Email" name="email" rules={[{ required: true, message: 'Please enter email' }, { type: 'email', message: 'Invalid email' }]}>
            <Input placeholder="admin@example.com" autoComplete="email" />
          </Form.Item>
          <Form.Item label="Password" name="password" rules={[{ required: true, message: 'Please enter password' }]}>
            <Input.Password placeholder="••••••••" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>Login</Button>
        </Form>
      </Card>
    </div>
  );
}


