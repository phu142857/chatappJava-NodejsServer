import { useEffect, useMemo, useState } from 'react';
import { App as AntdApp, Avatar, Button, Card, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Tooltip } from 'antd';
import { ExportOutlined, DownloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import apiClient from '../api/client';
import { API_BASE_URL } from '../config';
import { exportToCSV, exportToJSON } from '../utils/export';

type UserItem = {
  _id: string;
  username: string;
  email: string;
  avatar?: string;
  lastSeen?: string;
  role?: 'user' | 'admin' | 'moderator';
  isActive?: boolean;
  reportCount?: number;
  profile?: {
    firstName?: string;
    lastName?: string;
  };
};

type UsersResponse = {
  success: boolean;
  data: {
    users: UserItem[];
    pagination: { page: number; limit: number; total: number; pages: number };
  };
};

export default function Users() {
  const { modal, message: messageApi } = AntdApp.useApp();
  const [loading, setLoading] = useState(false);
  const [search, ] = useState('');
  const [data, setData] = useState<UserItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [includeInactive, setIncludeInactive] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState<null | UserItem>(null);
  const [roleOpen, setRoleOpen] = useState<null | UserItem>(null);
  const [resetPwOpen, setResetPwOpen] = useState<null | UserItem>(null);
  const [lockOpen, setLockOpen] = useState<null | UserItem>(null);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [roleForm] = Form.useForm();
  const [resetForm] = Form.useForm();
  const [lockForm] = Form.useForm();

  const resolveAvatarUrl = (avatar?: string) => {
    if (!avatar) return undefined;
    if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar;
    const base = API_BASE_URL.replace(/\/$/, '');
    // Avatar path from server is already in format /uploads/avatars/filename.jpg
    return `${base}${avatar}`;
  };

  const columns = useMemo(
    () => [
      {
        title: 'UUID',
        dataIndex: '_id',
        key: '_id',
        render: (id: string) => (
          <Tooltip title={id}>
            <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
              {id.length > 8 ? `${id.substring(0, 8)}...` : id}
            </span>
          </Tooltip>
        ),
      },
      {
        title: 'Username',
        dataIndex: 'username',
        key: 'username',
      },
      {
        title: 'Avatar',
        key: 'avatar',
        render: (_: unknown, record: UserItem) => (
          <Avatar size={32} src={resolveAvatarUrl(record.avatar)} icon={!record.avatar ? <span>{record.username?.[0]?.toUpperCase()}</span> : undefined} />
        ),
      },
      {
        title: 'Email',
        dataIndex: 'email',
        key: 'email',
      },
      {
        title: 'Active',
        key: 'isActive',
        render: (_: unknown, record: UserItem & { isActive?: boolean }) => (
          <Tag color={record.isActive ? 'green' : 'red'}>
            {record.isActive ? 'Active' : 'Inactive'}
          </Tag>
        ),
      },
      {
        title: 'Role',
        dataIndex: 'role',
        key: 'role',
        render: (r: UserItem['role']) => <Tag color={r === 'admin' ? 'magenta' : r === 'moderator' ? 'purple' : 'default'}>{r || 'user'}</Tag>
      },
      {
        title: 'Full Name',
        key: 'fullName',
        render: (_: unknown, record: UserItem) => (
          <span>
            {(record.profile?.firstName || '') + ' ' + (record.profile?.lastName || '')}
          </span>
        ),
      },
      {
        title: 'Last seen',
        dataIndex: 'lastSeen',
        key: 'lastSeen',
        render: (v?: string) => v ? dayjs(v).format('HH:mm DD/MM/YYYY') : '-',
      },
      {
        title: 'Reports',
        dataIndex: 'reportCount',
        key: 'reportCount',
        render: (count?: number) => (
          <Tag color={count && count > 0 ? 'red' : 'default'}>
            {count || 0}
          </Tag>
        ),
      },
    ],
    []
  );

  const fetchUsers = async (p = page, size = pageSize, s = search) => {
    setLoading(true);
    try {
      const { data } = await apiClient.get<UsersResponse>('/users', {
        params: { page: p, limit: size, search: s || undefined, includeInactive },
      });
      
      // Fetch report counts for each user
      const usersWithReports = await Promise.all(
        data.data.users.map(async (user) => {
          try {
            const reportsRes = await apiClient.get('/reports');
            const reports = reportsRes.data?.data?.reports || [];
            const reportCount = reports.filter((r: any) => r.target?._id === user._id).length;
            return { ...user, reportCount };
          } catch {
            return { ...user, reportCount: 0 };
          }
        })
      );
      
      setData(usersWithReports);
      setTotal(data.data.pagination.total);
      setPage(p);
      setPageSize(size);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers(1, pageSize, search);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleExport = (format: 'csv' | 'json') => {
    const exportData = data.map(user => ({
      ID: user._id,
      Username: user.username,
      Email: user.email,
      'Full Name': `${user.profile?.firstName || ''} ${user.profile?.lastName || ''}`.trim(),
      Role: user.role || 'user',
      'Is Active': user.isActive ? 'Yes' : 'No',
      'Last Seen': user.lastSeen ? dayjs(user.lastSeen).format('YYYY-MM-DD HH:mm:ss') : '-',
      'Report Count': user.reportCount || 0,
    }));

    if (format === 'csv') {
      exportToCSV(exportData, 'users', Object.keys(exportData[0] || {}));
    } else {
      exportToJSON(exportData, 'users');
    }
    messageApi.success(`Users exported as ${format.toUpperCase()}`);
  };

  return (
    <Card title="Users" extra={
      <Space>
        <Input.Search allowClear placeholder="Search by UUID, username, email, name" onSearch={(val) => fetchUsers(1, pageSize, val)} style={{ width: 360 }} />
        <Tooltip title={includeInactive ? 'Showing both locked and active' : 'Only showing active users'}>
          <Button onClick={() => { setIncludeInactive(!includeInactive); fetchUsers(1, pageSize, search); }}>
            {includeInactive ? 'Hide Locked' : 'Show Locked'}
          </Button>
        </Tooltip>
        {selectedRowKeys.length > 0 && (
          <Space>
            <Button 
              danger
              onClick={async () => {
                (modal || Modal).confirm({
                  title: `Delete ${selectedRowKeys.length} selected users?`,
                  content: 'This action cannot be undone.',
                  onOk: async () => {
                    try {
                      await Promise.all(
                        selectedRowKeys.map(id => 
                          apiClient.delete(`/users/${id}`).catch(err => {
                            console.error(`Failed to delete user ${id}:`, err);
                          })
                        )
                      );
                      messageApi.success(`Deleted ${selectedRowKeys.length} users`);
                      setSelectedRowKeys([]);
                      fetchUsers(page, pageSize, search);
                    } catch (e: any) {
                      messageApi.error('Some deletions failed');
                    }
                  }
                });
              }}
            >
              Delete Selected ({selectedRowKeys.length})
            </Button>
            <Button 
              onClick={async () => {
                try {
                  await Promise.all(
                    selectedRowKeys.map(id => 
                      apiClient.put(`/users/${id}/active`, { 
                        isActive: true,
                        reason: 'Bulk unlock by administrator'
                      }).catch(err => {
                        console.error(`Failed to unlock user ${id}:`, err);
                      })
                    )
                  );
                  messageApi.success(`Unlocked ${selectedRowKeys.length} users`);
                  setSelectedRowKeys([]);
                  fetchUsers(page, pageSize, search);
                } catch (e: any) {
                  messageApi.error('Some operations failed');
                }
              }}
            >
              Unlock Selected ({selectedRowKeys.length})
            </Button>
            <Button 
              danger
              onClick={async () => {
                try {
                  await Promise.all(
                    selectedRowKeys.map(id => 
                      apiClient.put(`/users/${id}/active`, { 
                        isActive: false,
                        reason: 'Bulk lock by administrator'
                      }).catch(err => {
                        console.error(`Failed to lock user ${id}:`, err);
                      })
                    )
                  );
                  messageApi.success(`Locked ${selectedRowKeys.length} users`);
                  setSelectedRowKeys([]);
                  fetchUsers(page, pageSize, search);
                } catch (e: any) {
                  messageApi.error('Some operations failed');
                }
              }}
            >
              Lock Selected ({selectedRowKeys.length})
            </Button>
          </Space>
        )}
        <Dropdown
          menu={{
            items: [
              {
                key: 'csv',
                label: 'Export as CSV',
                icon: <DownloadOutlined />,
                onClick: () => handleExport('csv'),
              },
              {
                key: 'json',
                label: 'Export as JSON',
                icon: <DownloadOutlined />,
                onClick: () => handleExport('json'),
              },
            ],
          }}
        >
          <Button icon={<ExportOutlined />}>Export</Button>
        </Dropdown>
      </Space>
    }>
      <Table
        rowKey="_id"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
        }}
        columns={[
          ...columns as any,
          {
            title: 'Actions',
            key: 'actions',
            render: (_: unknown, record: UserItem & { isActive?: boolean }) => (
              <Space>
                <Button size="small" onClick={() => { setEditOpen(record); editForm.setFieldsValue({ username: record.username, email: record.email, firstName: record.profile?.firstName, lastName: record.profile?.lastName }); }}>Edit</Button>
                <Button size="small" onClick={() => { setRoleOpen(record); roleForm.setFieldsValue({ role: record.role || 'user' }); }}>Role</Button>
                <Button size="small" onClick={() => { 
                  setLockOpen(record); 
                  lockForm.setFieldsValue({ 
                    isActive: !record.isActive,
                    reason: record.isActive ? '' : 'Account locked by administrator'
                  }); 
                }}>
                  {record.isActive ? 'Lock' : 'Unlock'}
                </Button>
                <Button size="small" danger onClick={async () => {
                  (modal || Modal).confirm({
                    title: `Delete account ${record.username}?`,
                    onOk: async () => {
                      try {
                        await apiClient.delete(`/users/${record._id}`);
                        messageApi.success('Deleted');
                        fetchUsers(page, pageSize, search);
                      } catch (e: any) {
                        messageApi.error(e?.response?.data?.message || 'Delete failed');
                      }
                    }
                  });
                }}>Delete</Button>
                <Button size="small" onClick={() => { setResetPwOpen(record); resetForm.resetFields(); }}>Reset MK</Button>
              </Space>
            )
          }
        ]}
        dataSource={data}
        pagination={{
          total,
          current: page,
          pageSize,
          showSizeChanger: true,
          onChange: (p, s) => fetchUsers(p, s, search),
        }}
      />
      <Modal title="Create Account" open={createOpen} onCancel={() => setCreateOpen(false)} onOk={() => createForm.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={createForm} onFinish={async (values) => {
          try {
            await apiClient.post('/users', { username: values.username, email: values.email, password: values.password, role: values.role, profile: { firstName: values.firstName, lastName: values.lastName } });
            messageApi.success('Account created'); setCreateOpen(false); createForm.resetFields(); fetchUsers(1, pageSize, search);
          } catch (e: any) {
            messageApi.error(e?.response?.data?.message || 'Create account failed');
          }
        }}>
          <Form.Item label="Username" name="username" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email' }]}><Input /></Form.Item>
          <Form.Item label="Password" name="password" rules={[{ required: true, min: 6 }]}><Input.Password /></Form.Item>
          <Form.Item label="Role" name="role" initialValue="user"><Select options={[{ value: 'user', label: 'User' }, { value: 'moderator', label: 'Moderator' }, { value: 'admin', label: 'Admin' }]} /></Form.Item>
          <Form.Item label="First Name" name="firstName"><Input /></Form.Item>
          <Form.Item label="Last Name" name="lastName"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`Edit: ${editOpen?.username}`} open={!!editOpen} onCancel={() => setEditOpen(null)} onOk={() => editForm.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={editForm} onFinish={async (values) => {
          try {
            await apiClient.put(`/users/${editOpen?._id}`, { username: values.username, email: values.email, profile: { firstName: values.firstName, lastName: values.lastName } });
            messageApi.success('Updated');
            setData(prev => prev.map(u => u._id === editOpen?._id ? { ...u, username: values.username, email: values.email, profile: { ...(u.profile||{}), firstName: values.firstName, lastName: values.lastName } } : u));
            setEditOpen(null);
            await fetchUsers(page, pageSize, search);
          } catch (e: any) {
            messageApi.error(e?.response?.data?.message || 'Update failed');
          }
        }}>
          <Form.Item label="Username" name="username" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="Email" name="email" rules={[{ required: true, type: 'email' }]}><Input /></Form.Item>
          <Form.Item label="First Name" name="firstName"><Input /></Form.Item>
          <Form.Item label="Last Name" name="lastName"><Input /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`Change Role: ${roleOpen?.username}`} open={!!roleOpen} onCancel={() => setRoleOpen(null)} onOk={() => roleForm.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={roleForm} onFinish={async (values) => {
          try {
            const response = await apiClient.put(`/users/${roleOpen?._id}/role`, { role: values.role });
            
            if (response.data.requireReauth) {
              // Check if the role change affects current user
              const currentUser = JSON.parse(localStorage.getItem('user') || '{}');
              if (response.data.targetUserId === currentUser._id) {
                // Current user's role was changed, force logout
                messageApi.success('Your role has been changed. Please log in again.');
                localStorage.clear();
                window.location.href = '/login';
                return;
              } else {
                // Another user's role was changed
                messageApi.success('Role changed. The user will need to log in again.');
              }
            } else {
              messageApi.success('Role changed');
            }
            
            setData(prev => prev.map(u => u._id === roleOpen?._id ? { ...u, role: values.role } : u));
            setRoleOpen(null);
            await fetchUsers(page, pageSize, search);
          } catch (error: any) {
            messageApi.error(error?.response?.data?.message || 'Failed to change role');
          }
        }}>
          <Form.Item label="Role" name="role" rules={[{ required: true }]}>
            <Select options={[{ value: 'user', label: 'User' }, { value: 'moderator', label: 'Moderator' }, { value: 'admin', label: 'Admin' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`Reset Password: ${resetPwOpen?.username}`} open={!!resetPwOpen} onCancel={() => setResetPwOpen(null)} onOk={() => resetForm.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={resetForm} onFinish={async (values) => {
          await apiClient.put(`/users/${resetPwOpen?._id}/reset-password`, { newPassword: values.newPassword });
          messageApi.success('Password reset'); setResetPwOpen(null);
        }}>
          <Form.Item label="New Password" name="newPassword" rules={[{ required: true, min: 6 }]}><Input.Password /></Form.Item>
        </Form>
      </Modal>

      <Modal title={`${lockOpen?.isActive ? 'Lock' : 'Unlock'} Account: ${lockOpen?.username}`} open={!!lockOpen} onCancel={() => setLockOpen(null)} onOk={() => lockForm.submit()} confirmLoading={loading}>
        <Form layout="vertical" form={lockForm} onFinish={async (values) => {
          try {
            await apiClient.put(`/users/${lockOpen?._id}/active`, { 
              isActive: values.isActive,
              reason: values.reason || (values.isActive ? 'Account unlocked by administrator' : 'Account locked by administrator')
            });
            messageApi.success(`Account ${values.isActive ? 'unlocked' : 'locked'} successfully`);
            setData(prev => prev.map(u => u._id === lockOpen?._id ? { ...u, isActive: values.isActive } : u));
            setLockOpen(null);
            fetchUsers(page, pageSize, search);
          } catch (e: any) {
            messageApi.error(e?.response?.data?.message || 'Operation failed');
          }
        }}>
          <Form.Item label="Action" name="isActive" rules={[{ required: true }]}>
            <Select>
              <Select.Option value={true}>Unlock Account</Select.Option>
              <Select.Option value={false}>Lock Account</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="Reason" name="reason" rules={[{ required: true, message: 'Please provide a reason' }]}>
            <Input.TextArea rows={3} placeholder="Enter reason for locking/unlocking this account" />
          </Form.Item>
        </Form>
      </Modal>

      <Button type="primary" style={{ position: 'fixed', right: 24, bottom: 24 }} onClick={() => { setCreateOpen(true); createForm.resetFields(); }}>Create Account</Button>
    </Card>
  );
}


